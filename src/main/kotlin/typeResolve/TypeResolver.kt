package typeResolve

import constraintGraphBuilder.*
import language.configReader.Context

class TypeResolver(private val context: Context) {
    suspend fun resolveFreely(expression: Expression, eventHandler: GraphSatisfactionEventHandler): Graph = ConstraintGraphBuilder(context)
        .buildFrom(
            expression as? Expression.FunctionCall
                ?: throw UnsupportedOperationException("Only function calls are currently supported as top-level expression")
        ).also { it.satisfyAllConstraints(eventHandler) }

}

interface GraphSatisfactionEventHandler {
    data class MatrixCell(val position: Position, val candidate: Symbol.Function) {
        val type get() = position.typeOn(candidate)
    }
    suspend fun handleStart(graph: Graph, unsatisfiedConstraints: HashSet<Constraint>)
    suspend fun handleStartingNewConstraint(constraint: Constraint)
    suspend fun handleTypeCheck(smaller: MatrixCell, greater: MatrixCell)
    suspend fun handleElimination(unmatched: MatrixCell, position: Position)
    suspend fun handleMatch(smaller: MatrixCell, greater: MatrixCell)
    suspend fun handleCompletion()
}

private suspend fun Graph.satisfyAllConstraints(eventHandler: GraphSatisfactionEventHandler) {
    val unsatisfiedConstraints = constraints
    eventHandler.handleStart(this, unsatisfiedConstraints)
    while (unsatisfiedConstraints.isNotEmpty())
        unsatisfiedConstraints += unsatisfiedConstraints.takeRandom()
            .also { eventHandler.handleStartingNewConstraint(it) }
            .satisfyAndGiveAllSiblingsThatMightBeUnsatisfied(eventHandler)
    eventHandler.handleCompletion()
}

private fun <E> HashSet<E>.takeRandom(): E = first().also { remove(it) }
