package language.configReader

import typeResolve.Type
import javax.inject.Inject

class SwiftyConfigReader @Inject constructor() : ConfigReader {

    private class SyntaxReader(private val statements: Iterator<String>) {
        var row: Int = -1
        var column: Int = 0

        val atEoF get() = atEoL && !statements.hasNext()
        val atEoL get() = column >= curLine.length
        lateinit var curLine: String
        init { nextLine() }

        val curChar get() = curLine[column]

        val atOpeningParenthesis get() = curChar == '('
        val atClosingParenthesis get() = curChar == ')'
        val atArrow get() = curChar == '-' && curLine[column + 1] == '>'
        val atColon get() = curChar == ':'
        val atComma get() = curChar == ','
        val atName get() = !(curChar.isWhitespace() || atOpeningParenthesis || atClosingParenthesis || atComma || atColon)

        private inline fun check(value: Boolean, message: () -> String) {
            if (!value) raise(message())
        }

        fun raise(message: String): Nothing = throw ConfigReader.SyntaxError(row, column, message)

        fun skipWhiteSpace() {
            while (!atEoL && curChar.isWhitespace()) column++
        }

        fun next() {
            column++
        }

        fun nextLine() {
            column = 0
            do {
                row++
            } while (statements.hasNext() && statements.next().also { curLine = it }.isEmpty())
        }

        fun readName(): String {
            checkNotAtEoL()
            check(atName) { "expected name, got '$curChar'" }
            val start = column
            do {
                next()
                if (atEoL)
                    return curLine.substring(start)
            } while (atName)

            return curLine.substring(start, column)
        }

        fun readMultipleTypes(builder: ContextBuilder): List<Type> {
            skipWhiteSpace()
            if (atClosingParenthesis)
                return emptyList()
            return mutableListOf<Type>().also { list ->
                while (!atEoL) {
                    list += readType(builder)
                    skipWhiteSpace()
                    if (atEoL || !atComma)
                        return@also
                    next()
                    skipWhiteSpace()
                }
            }
        }

        fun checkNotAtEoL() {
            check(!atEoL) { "unexpected EoL" }
        }

        fun readFunctionDefinition(builder: ContextBuilder) {
            skipWhiteSpace()
            val name = readName()
            checkNotAtEoL()
            check(atOpeningParenthesis) { "'(' expected in function definition" }
            next()
            readFunctionType(builder).also {
                builder.func(name)(*it.parameters.toTypedArray()) returns it.image
            }
        }

        fun readType(builder: ContextBuilder): Type {
            if (!atOpeningParenthesis)
                return readName().let { builder.typeByName(it) ?: raise("Type '$it' does not exist") }
            next()
            return readFunctionType(builder)
        }

        private fun readFunctionType(builder: ContextBuilder): Type.FunctionType {
            val parameterTypes = readMultipleTypes(builder)
            check(atClosingParenthesis) { "expected ')' in function type definition" }
            next()
            skipWhiteSpace()
            check(atArrow) { "expected -> to define function type image" }
            next()
            next()
            skipWhiteSpace()
            return Type.FunctionType(parameterTypes, readType(builder))
        }

        fun readTypeDefinition(builder: ContextBuilder) {
            skipWhiteSpace()
            val name = readName()
            skipWhiteSpace()
            if (atEoL) {
                builder.type(name)
                return
            }
            check(atColon) { "expected : in type definition" }
            next()
            skipWhiteSpace()
            @Suppress("UNCHECKED_CAST")
            builder.type(
                name,
                *(readMultipleTypes(builder).apply {
                    find { it !is Type.ConcreteType }?.let {
                        raise("type '$name' attempts to extend non-concrete type '$it'")
                    }
                } as List<Type.ConcreteType>).toTypedArray(),
            )
        }
    }

    override fun read(config: Iterator<String>) = context {
        val reader = SyntaxReader(config)
        reader.skipWhiteSpace()
        while (!reader.atEoF) {
            if (reader.atEoL) {
                reader.nextLine()
                reader.skipWhiteSpace()
                continue
            }

            when (val keyWord = reader.readName()) {
                "func" -> reader.readFunctionDefinition(this)
                "type" -> reader.readTypeDefinition(this)
                else -> reader.raise("'$keyWord' is not a valid keyword")
            }
            if (reader.atEoF) break
            reader.nextLine()
            reader.skipWhiteSpace()
        }
    }
}