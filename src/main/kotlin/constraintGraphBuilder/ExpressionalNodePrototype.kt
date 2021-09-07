package constraintGraphBuilder

import typeResolve.Expression
import typeResolve.Symbol

class ExpressionalNodePrototype(
    val expression: Expression.FunctionCall,
    val candidates: List<Symbol.Function>,
    val positions: List<PositionPrototype>,
    val constraints: List<ConstraintPrototype>,
) {
    val image: PositionPrototype.Image
        get() = positions.last() as? PositionPrototype.Image ?: throw IllegalStateException("last position must ALWAYS be Image")

    fun manufacture(entityHydrator: EntityHydrator) = ExpressionalNode(this, entityHydrator)
}