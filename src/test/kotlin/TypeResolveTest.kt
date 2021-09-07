import constraintGraphBuilder.types
import language.configReader.SwiftyConfigReader
import language.configReader.read
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import typeResolve.ExpressionParser
import typeResolve.Type
import typeResolve.TypeResolver

class TypeResolveTest {

//    @Test
//    fun `closure parameter being returned results in correct constraint direction`() {
//        Assertions.assertEquals(
//            Type.ConcreteType("B"),
//            TypeResolver(
//                SwiftyConfigReader().read("""
//                    type A
//                    type B: A
//
//                    func foo((A) -> B) -> A
//                    func foo((B) -> A) -> B
//                """.trimIndent())
//            ).resolveFreely(ExpressionParser().parse("foo { $0 }"))
//                .rootNode.image.types.single(),
//        )
//    }

    @Test
    fun `test paper example`() {
        Assertions.assertEquals(
            Type.ConcreteType("String"),
            TypeResolver(
                SwiftyConfigReader().read("""
                    type Int
                    type String
                    type MutableString: String
                    
                    func foo((Int) -> Int) -> Int
                    func foo((String) -> String) -> String
                    
                    func bar() -> MutableString
                    
                    func +(Int, Int) -> Int
                    func +(String, String) -> String
                """.trimIndent())
            ).resolveFreely(ExpressionParser().parse("foo { $0 + bar() }")).rootNode.image.types.single(),
        )
    }

    @Test
    fun `test paper example 2`() {
        Assertions.assertEquals(
            Type.ConcreteType("B"),
            TypeResolver(
                SwiftyConfigReader().read("""
                    type A
                    type B
                    
                    func ++(A, A) -> A
                    func ++(B, B) -> B
                    
                    func bar(B) -> B
                    
                    func foo((A) -> A) -> A
                    func foo((B) -> B) -> B
                """.trimIndent())
            ).resolveFreely(ExpressionParser().parse("foo { a -> foo { b -> foo { c -> a ++ b ++ bar(c) } } }"))
                .rootNode.image.types.single(),
        )
    }
}

