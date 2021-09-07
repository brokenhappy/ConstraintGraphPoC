package typeResolve

import constraintGraphBuilder.*
import language.configReader.Context

class TypeResolver(private val context: Context) {
    class UnsatisfiableConstraintsException : Exception("Constraint graph can not be satisfied")

    fun resolveFreely(expression: Expression): Graph = ConstraintGraphBuilder(context)
        .buildFrom(
            expression as? Expression.FunctionCall
                ?: throw UnsupportedOperationException("Only function calls are currently supported as top-level expression")
        ).also { it.satisfyAllConstraints() }

}
private fun Graph.satisfyAllConstraints() {
    val unsatisfiedConstraints = constraints
    while (unsatisfiedConstraints.isNotEmpty())
        unsatisfiedConstraints += unsatisfiedConstraints.takeRandom().satisfyAndGiveAllSiblingsThatMightBeUnsatisfied()
}

private fun <E> HashSet<E>.takeRandom(): E = first().also { remove(it) }
