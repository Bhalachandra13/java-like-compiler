package analysis;
import java.security.InvalidParameterException;
import java.util.*;

import com.sun.jdi.InternalException;
import notquitejava.ast.*;

public class ClassObjects {
    private final NQJClassDecl classDecl;

    public ClassObjects(NQJClassDecl classDecl) {
        this.classDecl = classDecl;
    }

    private final Analysis analysis = new Analysis(null);
    public final Map<String,NQJFunctionDecl> classMethods = new HashMap<>();

    public final Map<String,NQJVarDecl> classVarList = new HashMap<>();

    public NQJFunctionDecl lookupMethod(String methodName) {
        return  classMethods.get(methodName);
    }

    public NQJFunctionDecl insertMethod(String methodName ,NQJFunctionDecl methodDecl) {
        return classMethods.put(methodName,methodDecl);
    }

    public NQJClassDecl getClassDecl() {
        return classDecl;
    }
    public NQJVarDecl insertField(String fieldName, NQJVarDecl v) {
        return classVarList.put(fieldName,v);
    }
    public NQJVarDecl lookupField(String fieldName) { return classVarList.get(fieldName);}

    public NQJVarDecl getField(String name) {
       return classVarList.get(name);
    }
}
