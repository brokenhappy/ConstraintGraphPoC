import constraintGraphBuilder.Constraint
import constraintGraphBuilder.ConstraintGraphBuilder
import constraintGraphBuilder.types
import language.configReader.context
import language.configReader.f
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import typeResolve.Expression
import typeResolve.ExpressionParser
import typeResolve.Type

class ConstraintGraphBuilderTest {
    @Test
    fun `top level function call with no arguments has no constraints`() {
        val context = context {
            func("foo")() returns void
        }

        Assertions.assertEquals(
            setOf<Constraint>(),
            ConstraintGraphBuilder(context).buildFrom(
                ExpressionParser().parse("foo()") as Expression.FunctionCall
            ).constraints,
        )
    }

    @Test
    fun `function parameter must be subtype of argument image`() {
        val context = context {
            func("foo")(Type.int) returns void
            func("bar")() returns Type.double
        }

        ConstraintGraphBuilder(context).buildFrom(
            ExpressionParser().parse("foo(bar())") as Expression.FunctionCall
        ).constraints.first().also {
            Assertions.assertEquals(listOf(Type.int), it.tail.types.toList())
            Assertions.assertEquals(listOf(Type.double), it.head.types.toList())
        }
    }

    @Test
    fun `closure can be constrained to its argument`() {
        val context = context {
            func("foo")(f(Type.int) returns Type.double) returns void
        }

        ConstraintGraphBuilder(context).buildFrom(
            ExpressionParser().parse("foo({ a -> a })") as Expression.FunctionCall
        ).constraints.first().also {
            Assertions.assertEquals(listOf(Type.int), it.tail.types.toList())
            Assertions.assertEquals(listOf(Type.double), it.head.types.toList())
        }
    }

    @Test
    fun `closure uses name of inner closure in case parameter name shadows outer parameter name`() {
        val context = context {
            val int = type("Int")
            val double = type("Double")

            func("foo")(f(int) returns int) returns int
            func("bar")(f(double) returns int) returns double
        }

        ConstraintGraphBuilder(context).buildFrom(
            ExpressionParser().parse("foo({ a -> bar({ a -> a }) })") as Expression.FunctionCall
        ).constraints.first { it.head.node.call.name == "bar" }.also {
            Assertions.assertEquals(listOf(Type.double), it.tail.types.toList())
            Assertions.assertEquals(listOf(Type.int), it.head.types.toList())
        }
    }

    @Test
    fun `sadasd`() {
        val context = context {

//            type B
//            type C
//            type D
//
//            func abc() -> B
//            func abc() -> C
//            func abc() -> D
//
//            func cde(C) -> C
//            func cde(D) -> D
//
//            func +(D, D) -> D
//            func +(B, B) -> B
            val B = type("B")
            val C = type("C")
            val D = type("D")

            func("bcd")() returns B
            func("bcd")() returns C
            func("bcd")() returns D

            func("cd")(C) returns C
            func("cd")(D) returns D

            func("+")(D, D) returns D
            func("+")(B, B) returns B
        }

        ConstraintGraphBuilder(context).buildFrom(
            ExpressionParser().parse("cde(bcd()) + bcd()") as Expression.FunctionCall
        ).allSortedNodes.size.also { Assertions.assertEquals(4, it) }
    }
}