package typeResolve

sealed interface Type {
    infix fun isAssignableFrom(subType: Type): Boolean

    object Void : Type {
        override fun isAssignableFrom(subType: Type) = subType == Void
        override fun toString() = "Void"
    }

    object AnyObject : Type {
        override fun isAssignableFrom(subType: Type) = subType != Void
        override fun toString() = "AnyObject"
    }

    data class FunctionType(val parameters: List<Type>, val image: Type) : Type {
        override fun isAssignableFrom(subType: Type) = subType is FunctionType
                && subType.parameters.zip(parameters).all { (subParam, param) -> subParam.isAssignableFrom(param) }
                && image.isAssignableFrom(subType.image)

        override fun toString() = "(${parameters.joinToString()}) -> $image"
    }

    class ConcreteType(val name: String, vararg superTypes: ConcreteType) : Type {
        val superTypes: Set<ConcreteType> = (superTypes.flatMap { it.superTypes } + this).toSet()

        override fun equals(other: Any?) = other is ConcreteType && other.name == name
        override fun hashCode() = name.hashCode()
        override fun toString() = name

        override infix fun isAssignableFrom(subType: Type) = subType is ConcreteType && this in subType.superTypes
    }

    companion object {
        val int = ConcreteType("Int")
        val double = ConcreteType("Double")
        val string = ConcreteType("String")
    }
}