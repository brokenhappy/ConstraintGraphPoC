package typeResolve

sealed interface Symbol {
    data class Function(
        val name: String,
        val image: Type,
        val parameters: List<Type> = listOf()
    ) : Symbol {
        override fun toString() = Type.FunctionType(parameters, image).toString()
    }

    data class Variable(val name: String) : Symbol
}