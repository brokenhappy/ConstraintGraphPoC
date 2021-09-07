package language.configReader

import typeResolve.Symbol
import typeResolve.Type

data class Context private constructor(private val symbols: List<Symbol>, private val outerScope: Context?) {
    constructor(symbols: List<Symbol>) : this(symbols, null)

    data class ClosureParameterCount(val argumentIndex: Int, val parameterCount: Int)

    private val allSymbols: Sequence<Symbol>
        get() = symbols.asSequence() + (outerScope?.allSymbols ?: emptySequence())

    fun findFunctionsBy(name: String, parameterCount: Int, closureParameterCount: List<ClosureParameterCount>) =
        allSymbols.filter { symbol ->
            symbol is Symbol.Function && symbol.name == name && symbol.parameters.size == parameterCount && closureParameterCount.all {
                symbol.parameters[it.argumentIndex].let { argument ->
                    argument is Type.FunctionType && argument.parameters.size == it.parameterCount
                }
            }
        } as Sequence<Symbol.Function>

    fun findVariableBy(name: String) = allSymbols.find { it is Symbol.Variable && it.name == name } as Symbol.Variable?

    fun innerScopeWith(symbols: List<Symbol>): Context = Context(symbols, this)
}