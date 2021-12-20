package typeResolve

import org.jetbrains.annotations.Contract
import typeResolve.Expression.Closure
import kotlin.collections.HashSet

class ExpressionParser {
    class SyntaxError(val column: Int, message: String) : IllegalArgumentException(message)

    private class ExpressionReader(private val text: String) {
        private var index = 0.also { skipWhiteSpace() }
        private var currentNumberOfOpenedExpressionParentheses = 0
        val atEoL get() = index >= text.length
        private val curChar get() = text[index]
        private val atOpeningBracket get() = curChar == '{'
        private val atClosingBracket get() = curChar == '}'
        private val atOpeningParenthesis get() = curChar == '('
        private val atClosingParenthesis get() = curChar == ')'
        private val atArrow get() = curChar == '-' && text[index + 1] == '>'
        private val atComma get() = curChar == ','
        private val atName
            get() = !(atOpeningBracket || atClosingBracket || atOpeningParenthesis
                    || atClosingParenthesis || atComma || atArrow || curChar.isWhitespace())

        private fun next() {
            index++
        }

        private fun nextAndSkipWhiteSpace() {
            next()
            skipWhiteSpace()
        }

        fun skipWhiteSpace() {
            while (!atEoL && curChar.isWhitespace()) next()
        }

        @Contract("false -> fail")
        fun validate(value: Boolean) {
            validate(value) { "Incorrect syntax" }
        }

        @Contract("false, _ -> fail")
        inline fun validate(value: Boolean, message: () -> String) {
            if (!value)
                throw SyntaxError(index, message())
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun readClosureParameters() = buildList {
            skipWhiteSpace()
            val arrowLocation = text.indexOf("->", index)
            if (arrowLocation == -1)
                return@buildList
            val argumentText = text.substring(index, arrowLocation)
            if (argumentText.isEmpty() || argumentText.contains("{"))
                return@buildList
            val names = HashSet<String>()
            do {
                skipWhiteSpace()
                add(readName().also { validate(names.add(it)) { "Parameter names must be unique, found '$it' twice" } })
            } while (atComma.also { next() })
            index = arrowLocation + 2
        }

        private fun readName(): String {
            validate(!atEoL) { "unexpected EoL" }
            validate(atName) { "expected name, got $curChar instead at col $index" }
            val start = index
            do {
                next()
            } while (!atEoL && atName)
            return text.substring(start, index)
        }

        fun parseExpression(isAtStop: ExpressionReader.() -> Boolean): Expression {
            validate(!isAtStop()) { "Unexpected EoL, expected expression" }
            val leftHandSide = if (atOpeningParenthesis) {
                currentNumberOfOpenedExpressionParentheses++.also { next() }
                parseExpression(isAtStop)
            } else parseExpressionWithoutOperator()

            skipWhiteSpace()
            if (isAtStop())
                return leftHandSide
            if (atClosingParenthesis) {
                validate(currentNumberOfOpenedExpressionParentheses-- > 0) { "Unexpected ')'" }
                nextAndSkipWhiteSpace()
                return leftHandSide
            }
            val operator = readName()
            skipWhiteSpace()
            validate(!isAtStop()) { "Operator $operator does not have a right hand side" }
            return Expression.FunctionCall(operator, listOf(leftHandSide, parseExpression(isAtStop)))
        }

        private fun parseExpressionWithoutOperator(): Expression {
            validate(!atEoL) { "unexpected EoL, expected expression" }
            if (atOpeningBracket)
                return parseClosure()
            val name = readName()
            skipWhiteSpace()
            if (atEoL || !atOpeningParenthesis && !atOpeningBracket)
                return Expression.Variable(name)
            return Expression.FunctionCall(name, parseFunctionArguments())
        }

        // precondition: atOpeningParenthesis || atOpeningBracket
        private fun parseFunctionArguments(): List<Expression> {
            check(atOpeningParenthesis || atOpeningBracket)
            val parametersWithoutTrailingClosure =
                if (atOpeningBracket
                    || this
                        .also {
                            nextAndSkipWhiteSpace()
                            validate(!atEoL) { "Unexpected EoL, expected closing parenthesis" }
                        }
                        .atClosingParenthesis
                        .also { if (it) nextAndSkipWhiteSpace() }
                ) emptyList()
                else parseCommaSeperatedExpressions {
                    validate(!atEoL) { "Unexpected EoL, expected closing parenthesis or comma" }
                    atClosingParenthesis
                }.also {
                    validate(!atEoL || atClosingParenthesis)
                    nextAndSkipWhiteSpace()
                }

            return if (!atEoL && atOpeningBracket)
                parametersWithoutTrailingClosure + parseClosure()
            else parametersWithoutTrailingClosure
        }

        @OptIn(ExperimentalStdlibApi::class)
        private fun parseCommaSeperatedExpressions(isAtStop: ExpressionReader.() -> Boolean) = buildList {
            do {
                if (atComma)
                    nextAndSkipWhiteSpace()
                add(parseExpression { isAtStop() || atComma })
            } while (atComma)
        }

        private fun parseClosure(): Closure {
            check(atOpeningBracket)
            nextAndSkipWhiteSpace()
            validate(!atEoL) { "Unexpected EoL, expecting expression, closure parameters, or closing bracket" }
            return (if (atClosingBracket) Closure.Empty else readFilledClosure())
                .also {
                    skipWhiteSpace()
                    validate(!atEoL && atClosingBracket)
                    nextAndSkipWhiteSpace()
                }
        }

        private fun readFilledClosure(): Closure.Filled {
            val parameters = readClosureParameters().map { Expression.Variable(it) }.also { skipWhiteSpace() }
            return closure(parseExpression { atEoL || atClosingBracket }, parameters)
        }
    }

    fun parse(text: String) = ExpressionReader(text).parseExpression { atEoL }
}