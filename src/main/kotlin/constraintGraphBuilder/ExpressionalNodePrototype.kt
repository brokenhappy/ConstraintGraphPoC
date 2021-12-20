package constraintGraphBuilder

import typeResolve.Expression
import typeResolve.Symbol
import java.util.*

class ExpressionalNodePrototype(
    val expression: Expression.FunctionCall,
    val candidates: List<Symbol.Function>,
    val positions: List<PositionPrototype>,
    val constraints: List<ConstraintPrototype>,
    val allNodes: MutableList<ExpressionalNodePrototype>,
) {
    val image: PositionPrototype.Image = positions.last() as? PositionPrototype.Image
        ?: throw IllegalStateException("last position must ALWAYS be Image")

    fun manufacture(entityHydrator: EntityHydrator) =
        ExpressionalNode(this, entityHydrator, Stack<ExpressionalNodePrototype>().also { it.addAll(allNodes - this) })
    fun manufacture(entityHydrator: EntityHydrator, allNodes: Stack<ExpressionalNodePrototype>) =
        ExpressionalNode(this, entityHydrator, allNodes)
}