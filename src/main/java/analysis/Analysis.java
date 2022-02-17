package analysis;

import java.util.*;
import notquitejava.ast.*;

/**
 * Analysis visitor to handle most of the type rules specified to NQJ.
 */
public class Analysis extends NQJElement.DefaultVisitor {

    private final NQJProgram prog;
    private final List<TypeError> typeErrors = new ArrayList<>();
    private NameTable nameTable;
    private final LinkedList<TypeContext> ctxt = new LinkedList<>();

    //This is used to fetch the current class for this expression
    private static Stack<NQJClassDecl> curClassList = new Stack<>();

    //This is used to check cycle in inheritance
    private static final Map<String,String> inhMap = new HashMap<>();

    public void addError(NQJElement element, String message) {
        typeErrors.add(new TypeError(element, message));
    }

    public Analysis(NQJProgram prog) {
        this.prog = prog;
    }

    /**
     * This method is used to insert current class and it's parent class to curClassList.
     */
    public static void insertClass(NQJClassDecl classDecl) {
        curClassList.push(classDecl);
    }

    /**
     * This method is used to update the inheritance map.
     * This is updated when a class inherits from other class.
     * @return String This returns null if key doesn't exist else returns its value.
     */
    public String updateClassMap(String childClass, String parentClass) {
        return inhMap.put(childClass,parentClass);
    }

    /**
     * This method clears the curClassList variable after processing it.
     */
    public static void popClass()
    {
        curClassList.clear();
    }

    /**
     * This method is used to get the current class name (thisExpr).
     * This method also loads methods and fields from super classes except overridden methods.
     * @return String This returns the current class name especially for this expression.
     */
    public  String getCurClass() {

        if (curClassList.size() > 1) {
            NQJClassDecl childClass = curClassList.peek();
            ClassObjects temp = nameTable.lookupClassNode(childClass.getName());

            //copy all the fields and methods from superclass to this
            for (NQJClassDecl extendClass : curClassList) {
                if (childClass == extendClass) {
                    continue; //skip the last element as it is already defined.
                }
                ClassObjects parentClass = nameTable.lookupClassNode(extendClass.getName());
                parentClass.classVarList.forEach(temp.classVarList::putIfAbsent);
                parentClass.classMethods.forEach(temp.classMethods::putIfAbsent);
            }
            nameTable.updateClassObject(temp, childClass.getName());
        }
        if (curClassList.size() > 0) {
            return curClassList.peek().getName();
        }
        return null;
    }

    /**
     * Checks the saves NQJProgram for type errors.
     * Main entry point for type checking.
     * @return NQJProgram This returns the decorated syntax tree.
     */
    public NQJProgram check() {
        nameTable = new NameTable(this, prog);

        verifyMainMethod();

        checkOverriding();

        prog.accept(this);

        return prog;
    }

    /**
     * This method clears all the buffers.
     */
    public void clearAll() {
        NameTable.classList.clear();
        nameTable = null;
        curClassList.clear();
        ctxt.clear();
        inhMap.clear();
    }

    /**
     * This method verifies the main method body.
     */
    private void verifyMainMethod() {
        var main = nameTable.lookupFunction("main");
        if (main == null) {
            typeErrors.add(new TypeError(prog, "Method int main() must be present"));
            return;
        }
        if (!(main.getReturnType() instanceof NQJTypeInt)) {
            typeErrors.add(new TypeError(main.getReturnType(),
                    "Return type of the main method must be int"));
        }
        if (!(main.getFormalParameters().isEmpty())) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not take parameters"));
        }
        // Check if return statement is there as the last statement

        if (!isReturnExist(main)) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not have a return statement in all possible path"));
        }
    }

    /**
     * This methods checks if a function has return statement.
     * @param functionDecl This is function declaration.
     * @return This returns true if return statement exists.
     */
    public boolean isReturnExist(NQJFunctionDecl functionDecl) {
        NQJStatement last = null;
        for (int count = 0;count<functionDecl.getMethodBody().size();count++) {
            last = functionDecl.getMethodBody().get(count);
            if (last instanceof NQJStmtReturn && (count != functionDecl.getMethodBody().size()-1)) {
                addError(functionDecl.getMethodBody().get(count+1), "Unreachable statement");
            }
            if (last instanceof NQJBlock && (count != functionDecl.getMethodBody().size()-1)) {
                if(checkReturn(last)) {
                    addError(functionDecl.getMethodBody().get(count+1), "Unreachable statement");
                }
            }
        }
        return checkReturn(last);
    }

    /**
     * This method checks all posible paths of return.
     * @param statement This is the last statement of the function.
     * @return
     */
     public boolean checkReturn(NQJStatement statement) {
        if (statement instanceof NQJStmtIf) {
            var statementT = getLastElement(((NQJStmtIf) statement).getIfTrue());
            var statementF = getLastElement(((NQJStmtIf) statement).getIfFalse());
            boolean statementl = checkReturn(statementT);
            boolean statementr = checkReturn(statementF);
            if (statementl == statementr == true) {
                return true;
            }
        }
        else if(statement instanceof NQJStmtWhile) {
            var last = getLastElement(statement);
            if (last == null){
                return false;
            }
            if (checkReturn(last) == true) {
                return true;
            }
        }
        else if(statement instanceof NQJBlock) {
            var last = getLastElement(statement);
            if (checkReturn(last) == true) {
                return true;
            }
        }
        else if (statement instanceof NQJStmtReturn) {
            return true;
        }

        return false;
    }

    /**
     * This function traverse through the Block and returns last statement.
     * @param statements This can be block,method body or conditional statements.
     * @return This returns the last statement of the block
     */
    public NQJStatement getLastElement(NQJStatement statements) {
        NQJStatement last = null;
        if (statements instanceof NQJStmtWhile) {
            var condition = ((NQJStmtWhile) statements).getCondition();
            if (condition instanceof NQJBoolConst && (((NQJBoolConst) condition).getBoolValue() == true)) {
                statements = ((NQJStmtWhile) statements).getLoopBody();
            }
            else {
                return null;
            }
        }
        for (int count = 0; count < statements.size();count++) {
            last = (NQJStatement)statements.get(count);
        }
        return last;
    }


    @Override
    public void visit(NQJFunctionDecl m) {
        // parameter names are unique, build context
        TypeContext mctxt = this.ctxt.isEmpty()
                ? new TypeContextImpl(null, Type.INVALID)
                : this.ctxt.peek().copy();
        Set<String> paramNames = new HashSet<>();
        for (NQJVarDecl v : m.getFormalParameters()) {
            if (!paramNames.add(v.getName())) {
                addError(m, "Parameter with name " + v.getName() + " already exists.");
            }
            mctxt.putVar(v.getName(), type(v.getType()), v);
        }
        mctxt.setReturnType(type(m.getReturnType()));
        // enter method context
        ctxt.push(mctxt);

        m.getMethodBody().accept(this);

        // exit method context
        ctxt.pop();
        if (!isReturnExist(m)) {
            typeErrors.add(new TypeError(m.getFormalParameters(),
                    m.getName() + " method does not have a return statement in all possible path"));
        }
    }


    @Override public void visit(NQJClassDecl classDecl) {

        classDecl.getExtended().accept(this);
        Analysis.insertClass(classDecl);
        var name  = getCurClass();
        if(curClassList.size() > 1) {
            var extendsClass = (NQJExtendsClass)classDecl.getExtended();
            var ref = this.getNameTable().lookupClassNode(extendsClass.getName());
            classDecl.setDirectSuperClass(ref.getClassDecl());
        }
        classDecl.getFields().accept(this);
        classDecl.getMethods().accept(this);

        Analysis.popClass();
    }

    @Override public void visit(NQJNewObject newObject) {
        String className = newObject.getClassName();
        ClassObjects obj = nameTable.lookupClassNode(className);
        newObject.setClassDeclaration(obj.getClassDecl());
    }

    @Override
    public void visit(NQJExtendsClass extendsClass) {
        var ref = this.getNameTable().lookupClassNode(extendsClass.getName());
        if (ref != null) {
            Analysis.insertClass(ref.getClassDecl());
        }
        else
        {
            addError(extendsClass,"The class "+extendsClass.getName()+" is not defined");
        }
    }

    @Override
    public void visit(NQJStmtReturn stmtReturn) {
        Type actualReturn = checkExpr(ctxt.peek(), stmtReturn.getResult());
        Type expectedReturn = ctxt.getLast().getReturnType();
        if (!actualReturn.isSubtypeOf(expectedReturn)) {
            addError(stmtReturn, "Should return value of type " + expectedReturn
                    + ", but found " + actualReturn + ".");
        }
    }

    @Override
    public void visit(NQJStmtAssign stmtAssign) {
        Type lt = checkExpr(ctxt.peek(), stmtAssign.getAddress());
        Type rt = checkExpr(ctxt.peek(), stmtAssign.getValue());
        if (!rt.isSubtypeOf(lt)) {
            addError(stmtAssign.getValue(), "Cannot assign value of type " + rt
                    + " to " + lt + ".");
        }
    }

    @Override
    public void visit(NQJStmtExpr stmtExpr) {
        checkExpr(ctxt.peek(), stmtExpr.getExpr());
    }

    @Override
    public void visit(NQJStmtWhile stmtWhile) {
        Type ct = checkExpr(ctxt.peek(), stmtWhile.getCondition());
        if (!ct.isSubtypeOf(Type.BOOL)) {
            addError(stmtWhile.getCondition(),
                    "Condition of while-statement must be of type boolean, but this is of type "
                            + ct + ".");
        }
        super.visit(stmtWhile);
    }

    @Override
    public void visit(NQJStmtIf stmtIf) {
        Type ct = checkExpr(ctxt.peek(), stmtIf.getCondition());
        if (!ct.isSubtypeOf(Type.BOOL)) {
            addError(stmtIf.getCondition(),
                    "Condition of if-statement must be of type boolean, but this is of type "
                            + ct + ".");
        }
        super.visit(stmtIf);
    }

    @Override
    public void visit(NQJBlock block) {
        TypeContext bctxt = new TypeContextImpl(null, Type.INVALID);
        TypeContext paramList = this.ctxt.getLast();
        for (NQJStatement s : block) {
            // could also be integrated into the visitor run
            if (s instanceof NQJVarDecl) {
                NQJVarDecl varDecl = (NQJVarDecl) s;
                TypeContextImpl.VarRef ref = paramList.lookupVar(varDecl.getName());
                if (ref != null) {
                    addError(varDecl, "A variable with name " + varDecl.getName()
                            + " is already defined.");
                }
                else {
                    ref = bctxt.lookupVar(varDecl.getName());
                    if (ref != null) {
                        addError(varDecl, "A variable with name " + varDecl.getName()
                                + " is already defined.");
                    }

                    bctxt.putVar(varDecl.getName(), type(varDecl.getType()), varDecl);
                }
            } else {
                // enter block context
                ctxt.push(bctxt);
                s.accept(this);
                // exit block context
                ctxt.pop();
            }
        }
    }

    @Override
    public void visit(NQJVarDecl varDecl) {
        TypeContext bctxt = ctxt.isEmpty()
                ? new TypeContextImpl(null, Type.INVALID)
                : this.ctxt.peek().copy();
        TypeContextImpl.VarRef ref = bctxt.lookupVar(varDecl.getName());
        if (ref != null) {
            addError(varDecl, "A variable with name " + varDecl.getName()
                    + " is already defined.");
        }

        bctxt.putVar(varDecl.getName(), type(varDecl.getType()), varDecl);

    }

    public Type checkExpr(TypeContext ctxt, NQJExpr e) {
        return e.match(new ExprChecker(this, ctxt));
    }

    public Type checkExpr(TypeContext ctxt, NQJExprL e) {
        return e.match(new ExprChecker(this, ctxt));
    }

    /**
     * NQJ AST element to Type converter.
     */
    public Type type(NQJType type) {
        Type result =  type.match(new NQJType.Matcher<>() {


            @Override
            public Type case_TypeBool(NQJTypeBool typeBool) {
                return Type.BOOL;
            }

            @Override
            public Type case_TypeClass(NQJTypeClass typeClass) {
                ClassType obj = new ClassType(typeClass.getName());
                var ref = obj.getType(); //This returns Class Type
                return ref;
            }

            @Override
            public Type case_TypeArray(NQJTypeArray typeArray) {
                return nameTable.getArrayType(type(typeArray.getComponentType()));
            }

            @Override
            public Type case_TypeInt(NQJTypeInt typeInt) {
                return Type.INT;
            }

        });

        type.setType(result);
        return result;
    }

    public NameTable getNameTable() {
        return nameTable;
    }

    public TypeContext.VarRef lookupVar(String varName) {
        for (TypeContext item: ctxt) {
            TypeContext.VarRef ref = item.lookupVar(varName);
            if (ref == null) {
                continue;
            }
            return ref;
        }
        return null;
    }

    public List<TypeError> getTypeErrors() {
        return new ArrayList<>(typeErrors);
    }

    /**
     * This method checks for cycle in inheritance.
     * @param className This is the class name for which cyclic dependency needs to be calculated
     * @return boolean This return true if there exists a cycle
     */
    public static boolean isCyclicDependent(String className) {
        String key = className;

        while (key != null) {
            key = inhMap.get(key);
            if (className.equals(key)) {
                break;
            }
        }
        if(className.equals(key)) {
            return true;
        }
        return false;
    }

    /**
     * This method checks for overriden methods
     */
    public void checkOverriding() {
        for (var item: inhMap.values()){
            var childClass = getChildClasses(item);
            var parentClass = nameTable.lookupClassNode(item);
            if (!isCyclicDependent(item) && (parentClass != null)) {
                for (var child : childClass) {
                    var childObj = nameTable.lookupClassNode(child);
                    Set<String> methods = new HashSet<String>(parentClass.classMethods.keySet());
                    methods.retainAll(childObj.classMethods.keySet());
                    checkSignature(methods, item, child);
                }
            }
        }
    }

    /**
     * This method fetches overridden methods from class declaration
     * Late it compares the child and super class method signature
     * @param methods This contains a list of overridden methods between the classes.
     * @param parentClass This is the name of the parent class
     * @param childClass This is the child class name
     * @return boolean This returns true if all the methods matches signature
     */
    public boolean checkSignature(Set<String> methods,String parentClass,String childClass) {
        boolean flag = true;
        var parent = nameTable.lookupClassNode(parentClass);
        var child = nameTable.lookupClassNode(childClass);
        for (var method:methods) {
            var methodDeclParent = parent.classMethods.get(method);
            var methodDeclChild = child.classMethods.get(method);
            var p = methodDeclParent.getFormalParameters();
            var c = methodDeclChild.getFormalParameters();
            if (p.size() == c.size()) {
                for (int i = 0; i < p.size(); i++) {
                    if(!type(p.get(i).getType()).isSubtypeOf(type(c.get(i).getType()))) {
                        flag= false;
                    }
                }
            }
            else {
                flag = false;
            }
            if(!flag) {
                addError(methodDeclChild,methodDeclChild.getName() + " function signature is not matching with parent class");
            }
        }
        return flag;
    }

    /**
     * This method returns list of child classes for a class
     * @param className This is the name of the Parent class
     * @return This return set of child class names.Returns null if doesn't exist.
     */
    public Set<String> getChildClasses(String className) {
        Set<String> childClassList = new HashSet<>();
        for (Map.Entry<String,String> item :inhMap.entrySet()) {
            if (item.getValue().equals(className)) {
                childClassList.add(item.getKey());
            }
        }
        return childClassList;
    }

    /**
     * This method returns all the super classes
     * @param className This is the name of the child class
     * @return This returns set of super classes.Returns null if it doesn't inherit.
     */
    public static Set<String> getParentClass(String className) {
        Set<String> parentClass = new HashSet<>();
        if(!isCyclicDependent(className)) {
            String temp = className;
            while (temp != null) {
                 temp = inhMap.get(temp);
                if (temp!=null) {
                    parentClass.add(temp);
                }
            }
        }
        else {
            var ref = inhMap.get(className);
            parentClass.add(ref);
        }
        return parentClass;
    }
}
