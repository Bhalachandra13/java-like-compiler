package analysis;

import java.util.Set;

public class ClassType extends Type {
    private String baseType;
    private Set<String> superBaseType; //This stores the base type of all super classes

    public ClassType(String baseType) {
        this.baseType = baseType;
        this.superBaseType = Analysis.getParentClass(this.baseType);
    }

    @Override
    boolean isSubtypeOf(Type other) {
        if (other instanceof ClassType) {
            if(((ClassType) other).baseType.equals(this.baseType)) {
                return true;
            }
            else if(isSubTypeofParent(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method performs type checking with super classes
     * @param other Type of the left expression
     * @return This returns true if base type matches with one of the super classes
     */
    boolean isSubTypeofParent(Type other) {
        if (this.superBaseType.size()>0){
            for (var item : this.superBaseType) {
                if (((ClassType) other).baseType.equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Type getType() {
        return this;
    }

    public String toString() {
        return baseType;
    }
}
