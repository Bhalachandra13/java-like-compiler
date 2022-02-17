package analysis;

import java.util.*;
import notquitejava.ast.*;

/**
 * Name table for analysis class hierarchies.
 */
public class NameTable {
    private final Map<Type, ArrayType> arrayTypes = new HashMap<>();

    private final Map<String, NQJFunctionDecl> globalFunctions = new HashMap<>();

    //This contains declaration of all classes
    public static final Map<String,ClassObjects> classList = new HashMap<>();


    private final Analysis analysis;

    NameTable(Analysis analysis, NQJProgram prog) {
        this.analysis = analysis;
        globalFunctions.put("printInt", NQJ.FunctionDecl(NQJ.TypeInt(), "main",
                NQJ.VarDeclList(NQJ.VarDecl(NQJ.TypeInt(), "elem")), NQJ.Block()));
        for (NQJFunctionDecl f : prog.getFunctionDecls()) {
            var old = globalFunctions.put(f.getName(), f);
            if (old != null) {
                analysis.addError(f, "There already is a global function with name " + f.getName()
                        + " defined in " + old.getSourcePosition());
            }
        }

        updateCLassList(analysis, prog);
    }

    /**
     * This method stores the class declaration in the beginning
     * @param analysis This is the object of analysis class
     * @param prog This contains the syntax tree
     */
    public void updateCLassList(Analysis analysis,NQJProgram prog) {
        for (NQJClassDecl c: prog.getClassDecls()) {

            ClassObjects temp = new ClassObjects(c);
            for (NQJVarDecl v : c.getFields()) {
                var ref = temp.insertField(v.getName(), v);
                if (ref != null) {
                    analysis.addError(v, "There already is a field with name " + v.getName()
                            + " defined in " + ref.getSourcePosition());
                }
            }
            for (NQJFunctionDecl f : c.getMethods()) {
                var ref = temp.insertMethod(f.getName(), f);
                if (ref != null) {
                    analysis.addError(f, "There already is a method with name " + f.getName()
                            + " defined in " + ref.getSourcePosition());
                }
            }

            var extendsClass = c.getExtended();
            if (extendsClass instanceof NQJExtendsClass) {

                String parentClass = ((NQJExtendsClass) extendsClass).getName();
                var value = analysis.updateClassMap(c.getName(),parentClass );
                if (value != null) {
                    analysis.addError(c, c.getName() + " is already defined once ");
                }
                else {
                    if (Analysis.isCyclicDependent(c.getName())) {
                        analysis.addError(c, "There exist a cyclic dependency with the class "
                                + parentClass);
                    }
                }
            }
            var result = classList.put(c.getName(), temp);
            if (result != null) {
                analysis.addError(c,"The class "+c.getName() +" is already exist" +
                        " class name should be unique");
            }
        }
    }
    public NQJFunctionDecl lookupFunction(String functionName) {
        return globalFunctions.get(functionName);
    }

    /**
     * This function returns the classObject based on the requested class name
     * @param className This is the name of the requested class
     * @return ClassObjects This return the object which contains class declaration
     */
    public ClassObjects lookupClassNode(String className) {
        return  classList.get(className);
    }

    /**
     * This method updates the existing class object
     * This method is called only when a method inherits
     * @param obj Class Object of the class
     * @param className name of the class to be updated
     */
    public void updateClassObject(ClassObjects obj,String className) {
        classList.remove(className);
        classList.put(className,obj);
    }

    /**
     * Transform base type to array type.
     */
    public ArrayType getArrayType(Type baseType) {
        if (!arrayTypes.containsKey(baseType)) {
            arrayTypes.put(baseType, new ArrayType(baseType));
        }
        return arrayTypes.get(baseType);
    }

}
