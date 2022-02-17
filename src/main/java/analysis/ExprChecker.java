package analysis;

import analysis.TypeContext.VarRef;
import notquitejava.ast.*;

/**
 * Matcher implementation for expressions returning a NQJ type.
 */
public class ExprChecker implements NQJExpr.Matcher<Type>, NQJExprL.Matcher<Type> {
    private final Analysis analysis;
    private final TypeContext ctxt;

    public ExprChecker(Analysis analysis, TypeContext ctxt) {
        this.analysis = analysis;
        this.ctxt = ctxt;
    }

    Type check(NQJExpr e) {
        return e.match(this);
    }

    Type check(NQJExprL e) {
        return e.match(this);
    }

    void expect(NQJExpr e, Type expected) {
        Type actual = check(e);
        if (!actual.isSubtypeOf(expected)) {
            analysis.addError(e, "Expected expression of type " + expected
                    + " but found " + actual + ".");
        }
    }

    Type expectArray(NQJExpr e) {
        Type actual = check(e);
        if (!(actual instanceof ArrayType)) {
            analysis.addError(e, "Expected expression of array type,  but found " + actual + ".");
            return Type.ANY;
        } else {
            return actual;
        }
    }

    @Override
    public Type case_ExprUnary(NQJExprUnary exprUnary) {
        Type t = check(exprUnary.getExpr());
        return exprUnary.getUnaryOperator().match(new NQJUnaryOperator.Matcher<Type>() {

            @Override
            public Type case_UnaryMinus(NQJUnaryMinus unaryMinus) {
                expect(exprUnary.getExpr(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Negate(NQJNegate negate) {
                expect(exprUnary.getExpr(), Type.BOOL);
                return Type.BOOL;
            }
        });
    }

    /**
     * This method evaluates the type of the expression.
     * @param methodCall This contains the method called with receiver object.
     * @return This returns the return type of the method call.
     */
    @Override
    public Type case_MethodCall(NQJMethodCall methodCall) {
        String className = "";

        if (methodCall.getReceiver().toString() == "ExprThis") {
            methodCall.getReceiver().accept(analysis);
            className = analysis.getCurClass();
        }
        else if (methodCall.getReceiver() instanceof NQJNewObject) {
            methodCall.getReceiver().accept(analysis);
            className = ((NQJNewObject)methodCall.getReceiver()).getClassName();
        }
        else if (methodCall.getReceiver() instanceof NQJRead) {
            NQJRead obj = (NQJRead) methodCall.getReceiver();
            String varName = ((NQJVarUse)obj.getAddress()).getVarName();
            var ref = analysis.lookupVar(varName);
            if (ref == null) {
                var cRef = analysis.getNameTable().lookupClassNode(analysis.getCurClass()).getField(varName);
                if (cRef != null) {
                    className = cRef.getType().getType().toString();
                }
            }
            else {
                className = ref.type.toString();
            }
        }
        else if(methodCall.getReceiver() instanceof NQJMethodCall) {
             var type = analysis.checkExpr(ctxt,methodCall.getReceiver());
             className = type.toString();
        }


            try {
                NQJFunctionDecl m = analysis.getNameTable().lookupClassNode(className)
                        .lookupMethod(methodCall.getMethodName());
                NQJExprList args = methodCall.getArguments();
                NQJVarDeclList params = m.getFormalParameters();
                if (args.size() < params.size()) {
                    analysis.addError(methodCall, "Not enough arguments.");
                } else if (args.size() > params.size()) {
                    analysis.addError(methodCall, "Too many arguments.");
                } else {
                    for (int i = 0; i < params.size(); i++) {
                        expect(args.get(i), analysis.type(params.get(i).getType()));
                    }
                }
                methodCall.setFunctionDeclaration(m);
                return analysis.type(m.getReturnType());
            }
            catch (Exception e) {
                analysis.addError(methodCall,"The identifier is of type "+className
                +".The function "+methodCall.getMethodName()+" cannot be called");
            }
        return Type.ANY;
    }


    @Override
    public Type case_ArrayLength(NQJArrayLength arrayLength) {
        expectArray(arrayLength.getArrayExpr());
        return Type.INT;
    }

    @Override
    public Type case_ExprThis(NQJExprThis exprThis) {
        ClassType obj = new ClassType(analysis.getCurClass());
        return obj.getType();
    }


    @Override
    public Type case_ExprBinary(NQJExprBinary exprBinary) {
        return exprBinary.getOperator().match(new NQJOperator.Matcher<>() {
            @Override
            public Type case_And(NQJAnd and) {
                expect(exprBinary.getLeft(), Type.BOOL);
                expect(exprBinary.getRight(), Type.BOOL);
                return Type.BOOL;
            }

            @Override
            public Type case_Times(NQJTimes times) {
                return case_intOperation();
            }

            @Override
            public Type case_Div(NQJDiv div) {
                return case_intOperation();
            }

            @Override
            public Type case_Plus(NQJPlus plus) {
                return case_intOperation();
            }

            @Override
            public Type case_Minus(NQJMinus minus) {
                return case_intOperation();
            }

            private Type case_intOperation() {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Equals(NQJEquals equals) {
                Type l = check(exprBinary.getLeft());
                Type r = check(exprBinary.getRight());
                if (!l.isSubtypeOf(r) && !r.isSubtypeOf(l)) {
                    analysis.addError(exprBinary, "Cannot compare types " + l + " and " + r + ".");
                }
                return Type.BOOL;
            }

            @Override
            public Type case_Less(NQJLess less) {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.BOOL;
            }
        });
    }

    @Override
    public Type case_ExprNull(NQJExprNull exprNull) {
        return Type.NULL;
    }

    @Override
    public Type case_FunctionCall(NQJFunctionCall functionCall) {
        NQJFunctionDecl m = analysis.getNameTable().lookupFunction(functionCall.getMethodName());
        if (m == null) {
            analysis.addError(functionCall, "Function " + functionCall.getMethodName()
                    + " does not exists.");
            return Type.ANY;
        }
        NQJExprList args = functionCall.getArguments();
        NQJVarDeclList params = m.getFormalParameters();
        if (args.size() < params.size()) {
            analysis.addError(functionCall, "Not enough arguments.");
        } else if (args.size() > params.size()) {
            analysis.addError(functionCall, "Too many arguments.");
        } else {
            for (int i = 0; i < params.size(); i++) {
                expect(args.get(i), analysis.type(params.get(i).getType()));
            }
        }
        functionCall.setFunctionDeclaration(m);
        return analysis.type(m.getReturnType());
    }

    @Override
    public Type case_Number(NQJNumber number) {
        return Type.INT;
    }

    @Override
    public Type case_NewArray(NQJNewArray newArray) {
        expect(newArray.getArraySize(), Type.INT);
        ArrayType t = new ArrayType(analysis.type(newArray.getBaseType()));
        newArray.setArrayType(t);
        return t;
    }

    @Override
    public Type case_NewObject(NQJNewObject newObject) {
        ClassType obj = new ClassType(newObject.getClassName());
        var classDecl = analysis.getNameTable().lookupClassNode(newObject.getClassName()).getClassDecl();
        newObject.setClassDeclaration(classDecl);
        return obj.getType();
    }

    @Override
    public Type case_BoolConst(NQJBoolConst boolConst) {
        return Type.BOOL;
    }

    @Override
    public Type case_Read(NQJRead read) {
        return read.getAddress().match(this);
    }

    @Override
    public Type case_FieldAccess(NQJFieldAccess fieldAccess) {
        String className = "";

        if (fieldAccess.getReceiver().toString() == "ExprThis") {
            fieldAccess.getReceiver().accept(analysis);
            className = analysis.getCurClass();
        }
        else if(fieldAccess.getReceiver() instanceof NQJNewObject) {
            fieldAccess.getReceiver().accept(analysis);
            className = ((NQJNewObject)fieldAccess.getReceiver()).getClassName();
        }
        else if (fieldAccess.getReceiver() instanceof NQJRead)
        {
            NQJRead obj = (NQJRead) fieldAccess.getReceiver();
            String varName = ((NQJVarUse)obj.getAddress()).getVarName();
            var ref = analysis.lookupVar(varName);
            if (ref == null)
            {
                var cRef = analysis.getNameTable().lookupClassNode(analysis.getCurClass()).getField(varName);
                className = cRef.getType().getType().toString();
            }
            else {
                className = ref.type.toString();
            }

        }
        else if(fieldAccess.getReceiver() instanceof NQJMethodCall){
            var type = analysis.checkExpr(ctxt,fieldAccess.getReceiver());
            className = type.toString();
        }
        try {
            NQJVarDecl m = analysis.getNameTable().lookupClassNode(className).lookupField(fieldAccess.getFieldName());
            fieldAccess.setVariableDeclaration(m);
            return analysis.type(m.getType());
        }
        catch (Exception e) {
            analysis.addError(fieldAccess,"The field "+fieldAccess.getFieldName() + " is not defined in "+
                    className);
        }
        return Type.ANY;
    }

    @Override
    public Type case_VarUse(NQJVarUse varUse) {
        VarRef ref = analysis.lookupVar(varUse.getVarName());
        if (ref == null) {
            String varName = varUse.getVarName();
            try {
                var cRef = analysis.getNameTable().lookupClassNode(analysis.getCurClass())
                        .getField(varName);
                if (cRef == null) {
                    analysis.addError(varUse, "Variable " + varUse.getVarName() + " is not defined.");
                    return Type.ANY;
                }
                ref = new VarRef(analysis.type(cRef.getType()), cRef);
            }
            catch (NullPointerException e) {
                analysis.addError(varUse, "Variable " + varUse.getVarName() + " is not defined.");
                return Type.ANY;
            }
        }
        varUse.setVariableDeclaration(ref.decl);
        return ref.type;
    }

    @Override
    public Type case_ArrayLookup(NQJArrayLookup arrayLookup) {
        Type type = analysis.checkExpr(ctxt, arrayLookup.getArrayExpr());
        expect(arrayLookup.getArrayIndex(), Type.INT);
        if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            arrayLookup.setArrayType(arrayType);
            return arrayType.getBaseType();
        }
        analysis.addError(arrayLookup, "Expected an array for array-lookup, but found " + type);
        return Type.ANY;
    }
}
