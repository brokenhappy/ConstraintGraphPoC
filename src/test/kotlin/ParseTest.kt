import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import typeResolve.Expression
import typeResolve.Expression.Closure
import typeResolve.Expression.Variable
import typeResolve.ExpressionParser
import typeResolve.closure

class ParseTest {
    @Test
    fun `function call should become a function reference`() {
        Assertions.assertEquals(
            functionCall("foo"),
            ExpressionParser().parse("foo()"),
        )
    }

    @Test
    fun `nested call with same name but different argument count should be resolved`() {
        Assertions.assertEquals(
            functionCall("foo", functionCall("foo")),
            ExpressionParser().parse("foo(foo())"),
        )
    }

    @Test
    fun `variable as argument`() {
        Assertions.assertEquals(
            functionCall("foo", Variable("bar")),
            ExpressionParser().parse("foo(bar)"),
        )
    }

    @Test
    fun variable() {
        Assertions.assertEquals(
            Variable("foo"),
            ExpressionParser().parse("foo"),
        )
    }

    @Test
    fun `closure without arguments`() {
        Assertions.assertEquals(
            closure(functionCall("foo"), emptyList()),
            ExpressionParser().parse("{ foo() }"),
        )
    }

    @Test
    fun `closure with argument`() {
        Assertions.assertEquals(
            closure(functionCall("foo", Variable("bar")), listOf(Variable("bar"))),
            ExpressionParser().parse("{ bar -> foo(bar) }"),
        )
    }


    @Test
    fun `closure with multiple arguments`() {
        Assertions.assertEquals(
            closure(
                functionCall("threeParams", Variable("foo"), Variable("bar"), Variable("baz")),
                listOf(Variable("foo"), Variable("bar"), Variable("baz"))
            ),
            ExpressionParser().parse("{ foo, bar, baz -> threeParams(foo, bar, baz) }"),
        )
    }

    @Test
    fun `nested closures`() {
        Assertions.assertEquals(
            closure(
                closure(closure(functionCall("foo", Variable("bar")), emptyList()), emptyList()),
                listOf(Variable("bar"))
            ),
            ExpressionParser().parse("{ bar -> { { foo(bar) } } }"),
        )
    }

    @Test
    fun `edge case 1`() {
        Assertions.assertEquals(
            functionCall("foo", closure(Variable("a"), listOf(Variable("a")))),
            ExpressionParser().parse("foo({ a -> a })"),
        )
    }

    @Test
    fun `function call with space before opening bracket`() {
        Assertions.assertEquals(
            functionCall("foo"),
            ExpressionParser().parse("foo ()"),
        )
    }

    @Test
    fun `function call with space between brackets`() {
        Assertions.assertEquals(
            functionCall("foo"),
            ExpressionParser().parse("foo( )"),
        )
    }

    @Test
    fun `function call with space after bracket`() {
        Assertions.assertEquals(
            functionCall("foo"),
            ExpressionParser().parse("foo() "),
        )
    }

    @Test
    fun `operator call`() {
        Assertions.assertEquals(
            functionCall("+", functionCall("foo"), functionCall("foo")),
            ExpressionParser().parse("foo() + foo()"),
        )
    }

    @Test
    fun `empty closure`() {
        Assertions.assertEquals(
            functionCall("foo", Closure.Empty),
            ExpressionParser().parse("foo({ })"),
        )
    }

    @Test
    fun `trailing closure with parentheses`() {
        Assertions.assertEquals(
            functionCall("foo", Closure.Empty),
            ExpressionParser().parse("foo() { }"),
        )
    }

    @Test
    fun `trailing closure without parentheses`() {
        Assertions.assertEquals(
            functionCall("foo", Closure.Empty),
            ExpressionParser().parse("foo { }"),
        )
    }

    @Test
    fun `trailing closure with more then one argument`() {
        Assertions.assertEquals(
            functionCall("foo", Variable("bar"), functionCall("baz"), Closure.Empty),
            ExpressionParser().parse("foo(bar, baz()) { }"),
        )
    }

    @Test
    fun `closures operator closure`() {
        Assertions.assertEquals(
            functionCall("+", Closure.Empty, Closure.Empty),
            ExpressionParser().parse("{ } + { }"),
        )
    }

    @Test
    fun `function call with trailing closure operator closure`() {
        Assertions.assertEquals(
            functionCall("+", functionCall("foo", Closure.Empty), Closure.Empty),
            ExpressionParser().parse("foo { } + { }"),
        )
    }

    @Test
    fun `parenthesized operator expressions`() {
        Assertions.assertEquals(
            functionCall("+", functionCall("as", Variable("int"), Variable("Double")), Variable("double")),
            ExpressionParser().parse("(int as Double) + double"),
        )
    }

    @Nested
    inner class IllegalExpressions {
        @Test
        fun `trailing closure without closing bracket`() {
            assertIllegal("foo {")
        }

        @Test
        fun `function inside of function with missing closing parenthesis`() {
            assertIllegal("foo(cde()")
        }

        @Test
        fun `closure has extra opening bracket before closing`() {
            assertIllegal("foo { \$0 + bar() {}")
        }

        @Test
        fun `trailing closure without opening bracket`() {
            assertIllegal("foo }")
        }

        @Test
        fun `trailing closure without closing parenthesis`() {
            assertIllegal("foo (")
        }

        @Test
        fun `trailing closure without opening parenthesis`() {
            assertIllegal("foo )")
        }

        @Test
        fun `two empty closures`() {
            assertIllegal("{ } { }")
        }

        @Test
        fun `closure without closing bracket`() {
            assertIllegal("{ ")
        }

        @Test
        fun `only closing bracket`() {
            assertIllegal("}")
        }

        @Test
        fun `only opening parenthesis`() {
            assertIllegal("(")
        }

        @Test
        fun `only closing parenthesis`() {
            assertIllegal(")")
        }

        @Test
        fun `operator without right hand side`() {
            assertIllegal("foo +")
        }

        @Test
        fun `closure arguments with same name`() {
            assertIllegal("{ bar, bar -> bar }")
        }

        private fun assertIllegal(syntax: String) = assertThrows<ExpressionParser.SyntaxError> {
            ExpressionParser().parse(syntax)
        }
    }

    private fun functionCall(name: String, vararg arguments: Expression) =
        Expression.FunctionCall(name, arguments.toList())
}

