package typeResolve

sealed interface Expression {
    data class FunctionCall(val name: String, val arguments: List<Expression>) : Expression
    sealed interface Closure: Expression {
        val parameters: List<Variable>
        data class Filled(val expression: Expression, override val parameters: List<Variable>) : Closure
        object Empty: Closure {
            override val parameters = emptyList<Variable>()
        }
    }

    data class Variable(val name: String) : Expression
}

fun closure(expression: Expression, parameters: List<Expression.Variable>): Expression.Closure.Filled =
    Expression.Closure.Filled(
        expression,
        parameters.takeIf { it.isNotEmpty() }
            ?: (0..findHighestShorthandVariable(expression)).map { Expression.Variable("\$$it") },
    )

private val dollarAndDigits = "\\$\\d+".toRegex()
private fun findHighestShorthandVariable(expression: Expression): Int {
    if (expression is Expression.Variable && expression.name matches dollarAndDigits)
        return expression.name.substring(1).toInt()
    if (expression is Expression.FunctionCall)
        return expression.arguments.maxOfOrNull(::findHighestShorthandVariable) ?: 0
    return 0
}