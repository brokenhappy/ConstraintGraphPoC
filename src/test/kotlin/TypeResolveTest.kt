import constraintGraphBuilder.Constraint
import constraintGraphBuilder.Graph
import constraintGraphBuilder.Position
import constraintGraphBuilder.types
import language.configReader.SwiftyConfigReader
import language.configReader.read
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import typeResolve.ExpressionParser
import typeResolve.GraphSatisfactionEventHandler
import typeResolve.GraphSatisfactionEventHandler.MatrixCell
import typeResolve.Type
import typeResolve.TypeResolver

val dummyEventHandler = object : GraphSatisfactionEventHandler {
    override suspend fun handleStart(graph: Graph, unsatisfiedConstraints: HashSet<Constraint>) = Unit
    override suspend fun handleStartingNewConstraint(constraint: Constraint) = Unit
    override suspend fun handleTypeCheck(smaller: MatrixCell, greater: MatrixCell) = Unit
    override suspend fun handleElimination(unmatched: MatrixCell, position: Position) = Unit
    override suspend fun handleMatch(smaller: MatrixCell, greater: MatrixCell) = Unit
    override suspend fun handleCompletion() = Unit
}

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
    suspend fun `test paper example`() {
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
            ).resolveFreely(ExpressionParser().parse("foo { $0 + bar() }"), dummyEventHandler)
                .rootNode.image.types.single(),
        )
    }

    @Test
    suspend fun `test paper example 2`() {
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
            ).resolveFreely(
                ExpressionParser().parse("foo { a -> foo { b -> foo { c -> a ++ b ++ bar(c) } } }"),
                dummyEventHandler,
            ).rootNode.image.types.single(),
        )
    }
}

