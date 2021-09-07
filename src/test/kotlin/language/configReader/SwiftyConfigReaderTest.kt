package language.configReader

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SwiftyConfigReaderTest {
    @Test
    fun `function one definition`() {
        Assertions.assertEquals(
            context {
                val void = type("Void")
                func("foo")() returns void
            },
            SwiftyConfigReader().read(
                """
                    type Void
                    func foo() -> Void
                """.trimIndent()
            )
        )
    }

    @Test
    fun `function multiple definitions`() {
        Assertions.assertEquals(
            context {
                val int = type("Int")
                val double = type("Double")
                func("foo")() returns int
                func("foo")() returns double
            },
            SwiftyConfigReader().read(
                """
                    type Int
                    type Double
                    func foo() -> Int
                    func foo() -> Double
                """.trimIndent()
            )
        )
    }

    @Test
    fun `test closure type definition`() {
        Assertions.assertEquals(
            context {
                val int = type("Int")
                val double = type("Double")
                func("foo")() returns f(int).returns(double)
            },
            SwiftyConfigReader().read(
                """
                    type Int
                    type Double
                    func foo() -> (Int) -> Double
                """.trimIndent()
            )
        )
    }

    @Test
    fun `test type inheritance`() {
        Assertions.assertEquals(
            context {
                val int = type("Integer")
                val fraction = type("Fraction", int)
                func("foo")() returns fraction
            },
            SwiftyConfigReader().read(
                """
                    type Integer
                    type Fraction: Integer
                    func foo() -> Fraction
                """.trimIndent()
            )
        )
    }

    @Test
    fun `test whitespace prefix`() {
        Assertions.assertEquals(
            context { type("Int") },
            SwiftyConfigReader().read(" type Int")
        )
    }

    @Test
    fun `test newline and whitespace prefix`() {
        Assertions.assertEquals(
            context { type("Int") },
            SwiftyConfigReader().read( """
                | 
                | type Int
            """.trimMargin("|"))
        )
    }

    @Test
    fun `test multiple parameters`() {
        Assertions.assertEquals(
            context {
                val int = type("Integer")
                val fraction = type("Fraction", int)
                func("foo")(int, fraction) returns fraction
            },
            SwiftyConfigReader().read(
                """
                    type Integer
                    type Fraction: Integer
                    func foo(Integer, Fraction) -> Fraction
                """.trimIndent()
            )
        )
    }

    @Test
    fun `test empty line`() {
        Assertions.assertEquals(
            context { },
            SwiftyConfigReader().read("")
        )
    }

    @Test
    fun `test line with one space`() {
        Assertions.assertEquals(
            context { },
            SwiftyConfigReader().read(" ")
        )
    }

    @Test
    fun `test two lines with one space`() {
        Assertions.assertEquals(
            context { },
            SwiftyConfigReader().read(" \n ")
        )
    }

    @Test
    fun `test empty line before`() {
        Assertions.assertEquals(
            context {
                type("Integer")
            },
            SwiftyConfigReader().read("""
                
                type Integer
            """.trimIndent())
        )
    }

    @Test
    fun `test empty line after`() {
        Assertions.assertEquals(
            context {
                type("Integer")
            },
            SwiftyConfigReader().read("""
                type Integer
                
            """.trimIndent())
        )
    }

    @Test
    fun `test infix empty line`() {
        Assertions.assertEquals(
            context {
                type("Integer")
                type("Fractional")
            },
            SwiftyConfigReader().read("""
                type Integer
                
                type Fractional
            """.trimIndent())
        )
    }
}

