package language.configReader

import typeResolve.Symbol.Function
import typeResolve.Symbol
import typeResolve.Type

interface ArgumentBuilder {
    operator fun invoke(vararg params: Type): ReturnBuilder
}

interface ReturnBuilder {
    infix fun returns(type: Type)
}

interface ContextBuilder {
    val void: Type.Void

    fun func(name: String): ArgumentBuilder
    fun variable(name: String)
    fun typeByName(name: String): Type?
    fun type(name: String, vararg superTypes: Type.ConcreteType): Type.ConcreteType
}

fun ContextBuilder.f(vararg parameterTypes: Type) = object: FunctionTypeBuilder {
    override fun returns(type: Type) = Type.FunctionType(parameterTypes.toList(), type)
}

interface FunctionTypeBuilder {
    infix fun returns(type: Type): Type.FunctionType
}

private class ContextBuilderImplementation : ContextBuilder {
    override val void = Type.Void
    val buildingSymbols = mutableSetOf<Symbol>()
    val types = mutableSetOf<Type>()
    override fun func(name: String) = object : ArgumentBuilder {
        override operator fun invoke(vararg params: Type) = object : ReturnBuilder {
            override fun returns(type: Type) {
                val newFunction = Function(name, type, params.toList())
                if (newFunction in buildingSymbols) throw IllegalArgumentException(
                    "signature already exists: func $name(${params.joinToString()}) -> $type"
                )
                buildingSymbols += newFunction
            }
        }
    }

    override fun variable(name: String) {
        buildingSymbols += Symbol.Variable(name)
    }

    override fun typeByName(name: String) = types.find { it is Type.ConcreteType && it.name == name }
    override fun type(name: String, vararg superTypes: Type.ConcreteType) =
        Type.ConcreteType(name, *superTypes).also { types += it }
}

fun context(builder: ContextBuilder.() -> Unit) =
    Context(ContextBuilderImplementation().also { it.builder() }.buildingSymbols.toList())
