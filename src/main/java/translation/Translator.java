package translation;

import analysis.ArrayType;
import com.sun.jdi.ClassType;
import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static frontend.AstPrinter.print;
import static minillvm.ast.Ast.*;


/**
 * Entry class for the translation phase.
 */
public class Translator {

    private final StmtTranslator stmtTranslator = new StmtTranslator(this);
    private final ExprLValue exprLValue = new ExprLValue(this);
    private final ExprRValue exprRValue = new ExprRValue(this);
    private final Map<NQJFunctionDecl, Proc> functionImpl = new HashMap<>();
    private final Prog prog = Prog(TypeStructList(), GlobalList(), ProcList());
    private final NQJProgram javaProg;
    private final Map<NQJVarDecl, TemporaryVar> localVarLocation = new HashMap<>();
    private final Map<analysis.Type, Type> translatedType = new HashMap<>();
    private final Map<Type, TypeStruct> arrayStruct = new HashMap<>();
    private final Map<Type, Proc> newArrayFuncForType = new HashMap<>();

    private final Map<Type,TypeStruct> classStruct = new HashMap<>();

    public static final Map<String,Map<NQJFunctionDecl,Proc>> vTable = new HashMap<>();
    private final Map<NQJFunctionDecl, Proc> methodImpl = new HashMap<>();
    private final Map<NQJVarDecl,TemporaryVar> classVariables = new HashMap<>();

    // mutable state
    private Proc currentProcedure;
    private BasicBlock currentBlock;

    public Translator(NQJProgram javaProg) {
        this.javaProg = javaProg;
    }

    /**
     * Translates given program into a mini llvm program.
     */
    public Prog translate() {

        translateClasses();
        // translate functions except main
        // has only access to functions
        translateFunctions();

        // translate main function
        // has access to functions
        translateMainFunction();

        finishNewArrayProcs();

        vTable.clear();
        classVariables.clear();
        return prog;
    }

    TemporaryVar getLocalVarLocation(NQJVarDecl varDecl) {
        return localVarLocation.get(varDecl);
    }

    private void finishNewArrayProcs() {
        for (Type type : newArrayFuncForType.keySet()) {
            finishNewArrayProc(type);
        }
    }

    private void finishNewArrayProc(Type componentType) {
        final Proc newArrayFunc = newArrayFuncForType.get(componentType);
        final Parameter size = newArrayFunc.getParameters().get(0);

        addProcedure(newArrayFunc);
        setCurrentProc(newArrayFunc);

        BasicBlock init = newBasicBlock("init");
        addBasicBlock(init);
        setCurrentBlock(init);
        TemporaryVar sizeLessThanZero = TemporaryVar("sizeLessThanZero");
        addInstruction(BinaryOperation(sizeLessThanZero,
                VarRef(size), Slt(), ConstInt(0)));
        BasicBlock negativeSize = newBasicBlock("negativeSize");
        BasicBlock goodSize = newBasicBlock("goodSize");
        currentBlock.add(Branch(VarRef(sizeLessThanZero), negativeSize, goodSize));

        addBasicBlock(negativeSize);
        negativeSize.add(HaltWithError("Array Size must be positive"));

        addBasicBlock(goodSize);
        setCurrentBlock(goodSize);

        // allocate space for the array

        TemporaryVar arraySizeInBytes = TemporaryVar("arraySizeInBytes");
        addInstruction(BinaryOperation(arraySizeInBytes,
                VarRef(size), Mul(), byteSize(componentType)));

        // 4 bytes for the length
        TemporaryVar arraySizeWithLen = TemporaryVar("arraySizeWitLen");
        addInstruction(BinaryOperation(arraySizeWithLen,
                VarRef(arraySizeInBytes), Add(), ConstInt(4)));

        TemporaryVar mallocResult = TemporaryVar("mallocRes");
        addInstruction(Alloc(mallocResult, VarRef(arraySizeWithLen)));
        TemporaryVar newArray = TemporaryVar("newArray");
        addInstruction(Bitcast(newArray,
                getArrayPointerType(componentType), VarRef(mallocResult)));

        // store the size
        TemporaryVar sizeAddr = TemporaryVar("sizeAddr");
        addInstruction(GetElementPtr(sizeAddr,
                VarRef(newArray), OperandList(ConstInt(0), ConstInt(0))));
        addInstruction(Store(VarRef(sizeAddr), VarRef(size)));

        // initialize Array with zeros:
        final BasicBlock loopStart = newBasicBlock("loopStart");
        final BasicBlock loopBody = newBasicBlock("loopBody");
        final BasicBlock loopEnd = newBasicBlock("loopEnd");
        final TemporaryVar iVar = TemporaryVar("iVar");
        currentBlock.add(Alloca(iVar, TypeInt()));
        currentBlock.add(Store(VarRef(iVar), ConstInt(0)));
        currentBlock.add(Jump(loopStart));

        // loop condition: while i < size
        addBasicBlock(loopStart);
        setCurrentBlock(loopStart);
        final TemporaryVar i = TemporaryVar("i");
        final TemporaryVar nextI = TemporaryVar("nextI");
        loopStart.add(Load(i, VarRef(iVar)));
        TemporaryVar smallerSize = TemporaryVar("smallerSize");
        addInstruction(BinaryOperation(smallerSize,
                VarRef(i), Slt(), VarRef(size)));
        currentBlock.add(Branch(VarRef(smallerSize), loopBody, loopEnd));

        // loop body
        addBasicBlock(loopBody);
        setCurrentBlock(loopBody);
        // ar[i] = 0;
        final TemporaryVar iAddr = TemporaryVar("iAddr");
        addInstruction(GetElementPtr(iAddr,
                VarRef(newArray), OperandList(ConstInt(0), ConstInt(1), VarRef(i))));
        addInstruction(Store(VarRef(iAddr), defaultValue(componentType)));

        // nextI = i + 1;
        addInstruction(BinaryOperation(nextI, VarRef(i), Add(), ConstInt(1)));
        // store new value in i
        addInstruction(Store(VarRef(iVar), VarRef(nextI)));

        loopBody.add(Jump(loopStart));

        addBasicBlock(loopEnd);
        loopEnd.add(ReturnExpr(VarRef(newArray)));
    }

    public void translateClasses() {
        for (NQJClassDecl classDecl : javaProg.getClassDecls()) {
            for (NQJFunctionDecl functionDecl : classDecl.getMethods()) {
                initMethods(functionDecl);
            }
            allocaCLassVars(classDecl.getFields());
            for (NQJFunctionDecl functionDecl : classDecl.getMethods()) {
                tranlateMethod(functionDecl);
            }
            vTable.put(classDecl.getName(),methodImpl);
        }
    }

    public void initMethods(NQJFunctionDecl f) {
        Type returnType = translateType(f.getReturnType());
        ParameterList params = f.getFormalParameters()
                .stream()
                .map(p -> Parameter(translateType(p.getType()), p.getName()))
                .collect(Collectors.toCollection(Ast::ParameterList));
        Proc proc = Proc(f.getName(), returnType, params, BasicBlockList());
        addProcedure(proc);
        methodImpl.put(f, proc);
    }

    public void tranlateMethod(NQJFunctionDecl m) {
        Proc proc = methodImpl.get(m);
        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);

        localVarLocation.clear();

        // store copies of the parameters in Allocas, to make uniform read/write access possible
        int i = 0;
        for (NQJVarDecl param : m.getFormalParameters()) {
            TemporaryVar v = TemporaryVar(param.getName());
            addInstruction(Alloca(v, translateType(param.getType())));
            addInstruction(Store(VarRef(v), VarRef(proc.getParameters().get(i))));
            localVarLocation.put(param, v);
            i++;
        }

        // allocate space for the local variables
        allocaLocalVars(m.getMethodBody());

        translateStmt(m.getMethodBody());

    }

    private void translateFunctions() {
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            initFunction(functionDecl);
        }
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            translateFunction(functionDecl);
        }
    }

    private void translateMainFunction() {
        NQJFunctionDecl f = null;
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                f = functionDecl;
                break;
            }
        }

        if (f == null) {
            throw new IllegalStateException("Main function expected");
        }

        Proc proc = Proc("main", TypeInt(), ParameterList(), BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);

        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);

        // allocate space for the local variables
        allocaLocalVars(f.getMethodBody());

        // translate
        translateStmt(f.getMethodBody());
    }

    private void initFunction(NQJFunctionDecl f) {
        Type returnType = translateType(f.getReturnType());
        ParameterList params = f.getFormalParameters()
                .stream()
                .map(p -> Parameter(translateType(p.getType()), p.getName()))
                .collect(Collectors.toCollection(Ast::ParameterList));
        Proc proc = Proc(f.getName(), returnType, params, BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);
    }

    private void translateFunction(NQJFunctionDecl m) {
        Proc proc = functionImpl.get(m);
        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);

        localVarLocation.clear();

        // store copies of the parameters in Allocas, to make uniform read/write access possible
        int i = 0;
        for (NQJVarDecl param : m.getFormalParameters()) {
            TemporaryVar v = TemporaryVar(param.getName());
            addInstruction(Alloca(v, translateType(param.getType())));
            addInstruction(Store(VarRef(v), VarRef(proc.getParameters().get(i))));
            localVarLocation.put(param, v);
            i++;
        }

        // allocate space for the local variables
        allocaLocalVars(m.getMethodBody());

        translateStmt(m.getMethodBody());
    }

    void translateStmt(NQJStatement s) {
        addInstruction(CommentInstr(sourceLine(s) + " start statement : " + printFirstline(s)));
        s.match(stmtTranslator);
        addInstruction(CommentInstr(sourceLine(s) + " end statement: " + printFirstline(s)));
    }

    int sourceLine(NQJElement e) {
        while (e != null) {
            if (e.getSourcePosition() != null) {
                return e.getSourcePosition().getLine();
            }
            e = e.getParent();
        }
        return 0;
    }

    private String printFirstline(NQJStatement s) {
        String str = print(s);
        str = str.replaceAll("\n.*", "");
        return str;
    }

    BasicBlock newBasicBlock(String name) {
        BasicBlock block = BasicBlock();
        block.setName(name);
        return block;
    }

    void addBasicBlock(BasicBlock block) {
        currentProcedure.getBasicBlocks().add(block);
    }

    BasicBlock getCurrentBlock() {
        return currentBlock;
    }

    void setCurrentBlock(BasicBlock currentBlock) {
        this.currentBlock = currentBlock;
    }


    void addProcedure(Proc proc) {
        prog.getProcedures().add(proc);
    }

    void setCurrentProc(Proc currentProc) {
        if (currentProc == null) {
            throw new RuntimeException("Cannot set proc to null");
        }
        this.currentProcedure = currentProc;
    }

    private void allocaLocalVars(NQJBlock methodBody) {
        methodBody.accept(new NQJElement.DefaultVisitor() {
            @Override
            public void visit(NQJVarDecl localVar) {
                super.visit(localVar);
                TemporaryVar v = TemporaryVar(localVar.getName());
                addInstruction(Alloca(v, translateType(localVar.getType())));
                localVarLocation.put(localVar, v);
            }
        });
    }

    private void allocaCLassVars(NQJVarDeclList classVarList) {
        for (var classVar : classVarList) {
            TemporaryVar v = TemporaryVar(classVar.getName());
            addInstruction(Alloca(v, translateType(classVar.getType())));
            classVariables.put(classVar,v);
        }
    }

    void addInstruction(Instruction instruction) {
        currentBlock.add(instruction);
    }

    Type translateType(NQJType type) {
        return translateType(type.getType());
    }

    Type  translateType(analysis.Type t) {
        Type result = translatedType.get(t);
        if (result == null) {
            if (t == analysis.Type.INT) {
                result = TypeInt();
            } else if (t == analysis.Type.BOOL) {
                result = TypeBool();
            } else if (t instanceof ArrayType) {
                ArrayType at = (ArrayType) t;
                result = TypePointer(getArrayStruct(translateType(at.getBaseType())));
            } else if(t instanceof ClassType) {
                ClassType ct = (ClassType) t;

                //result = TypePointer(getClassStruct(t));
                //return result;
            }
            translatedType.put(t, result);
        }
        return result;
    }

    Parameter getThisParameter() {
        // in our case 'this' is always the first parameter
        return currentProcedure.getParameters().get(0);
    }

    Operand exprLvalue(NQJExprL e) {
        return e.match(exprLValue);
    }

    Operand exprRvalue(NQJExpr e) {
        return e.match(exprRValue);
    }

    void addNullcheck(Operand arrayAddr, String errorMessage) {
        TemporaryVar isNull = TemporaryVar("isNull");
        addInstruction(BinaryOperation(isNull, arrayAddr.copy(), Eq(), Nullpointer()));

        BasicBlock whenIsNull = newBasicBlock("whenIsNull");
        BasicBlock notNull = newBasicBlock("notNull");
        currentBlock.add(Branch(VarRef(isNull), whenIsNull, notNull));

        addBasicBlock(whenIsNull);
        whenIsNull.add(HaltWithError(errorMessage));

        addBasicBlock(notNull);
        setCurrentBlock(notNull);
    }

    Operand getArrayLen(Operand arrayAddr) {
        TemporaryVar addr = TemporaryVar("length_addr");
        addInstruction(GetElementPtr(addr,
                arrayAddr.copy(), OperandList(ConstInt(0), ConstInt(0))));
        TemporaryVar len = TemporaryVar("len");
        addInstruction(Load(len, VarRef(addr)));
        return VarRef(len);
    }

    public Operand getNewArrayFunc(Type componentType) {
        Proc proc = newArrayFuncForType.computeIfAbsent(componentType, this::createNewArrayProc);
        return ProcedureRef(proc);
    }

    private Proc createNewArrayProc(Type componentType) {
        Parameter size = Parameter(TypeInt(), "size");
        return Proc("newArray",
                getArrayPointerType(componentType), ParameterList(size), BasicBlockList());
    }

    private Type getArrayPointerType(Type componentType) {
        return TypePointer(getArrayStruct(componentType));
    }

    TypeStruct getArrayStruct(Type type) {
        return arrayStruct.computeIfAbsent(type, t -> {
            TypeStruct struct = TypeStruct("array_" + type, StructFieldList(
                    StructField(TypeInt(), "length"),
                    StructField(TypeArray(type, 0), "data")
            ));
            prog.getStructTypes().add(struct);
            return struct;
        });
    }

//   private Type getVTClassPointerType(String className) { return TypePointer(getClassVTableStruct(className));}

   /*
    public TypeStruct getClassVTableStruct(String className) {

        TypeStruct struct = TypeStruct("vtable_"+className, t --> StructFieldList(
                StructField(TypeInt(),"dummy"))
        );
        for (var item : methodImpl.entrySet()) {
            struct.getFields().add(StructField(procedureType(item.getValue()),item.getValue().getName()));
        }
        return struct;
    }*/

    private Type procedureType(Proc procedure) {
        Type resultType = procedure.getReturnType();
        TypeRefList argTypes = Ast.TypeRefList();
        for (Parameter p : procedure.getParameters()) {
            argTypes.add(p.getType());
        }
        return Ast.TypeProc(argTypes, resultType);
    }

    /*
    public TypeStruct setClassStruct(analysis.Type type,NQJClassDecl classDecl) {

        TypeStruct struct = TypeStruct("class_"+type.toString(), t --> StructFieldList(
                StructField(getVTClassPointerType(type.toString()),"vtable"))
        );
        return struct;
    }*/

    Operand addCastIfNecessary(Operand value, Type expectedType) {
        if (expectedType.equalsType(value.calculateType())) {
            return value;
        }
        TemporaryVar castValue = TemporaryVar("castValue");
        addInstruction(Bitcast(castValue, expectedType, value));
        return VarRef(castValue);
    }

    BasicBlock unreachableBlock() {
        return BasicBlock();
    }

    Type getCurrentReturnType() {
        return currentProcedure.getReturnType();
    }

    public Proc loadFunctionProc(NQJFunctionDecl functionDeclaration) {
        return functionImpl.get(functionDeclaration);
    }

    public Proc loadMethodProc(String className, NQJFunctionDecl functionDecl) {
        return lookUpClassNode(className).get(functionDecl);
    }

    public Map<NQJFunctionDecl, Proc> lookUpClassNode(String className) {
        return vTable.get(className);
    }
    /**
     * return the number of bytes required by the given type.
     */
    public Operand byteSize(Type type) {
        return type.match(new Type.Matcher<>() {
            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(4);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                return Sizeof(typeStruct);
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return ConstInt(8);
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return ConstInt(8);
            }
        });
    }

    private Operand defaultValue(Type componentType) {
        return componentType.match(new Type.Matcher<>() {
            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstBool(false);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return Nullpointer();
            }
        });
    }
}
