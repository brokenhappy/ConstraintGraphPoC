package constraintGraphBuilder

import constraintGraphBuilder.PositionPrototype.*
import language.configReader.Context
import typeResolve.Expression
import typeResolve.Symbol.Function
import typeResolve.Type
import java.util.*


sealed interface Position {
    val node: ExpressionalNode
    fun typeOn(candidate: Function): Type
    data class Argument(override val node: ExpressionalNode, val index: Int) : Position {
        override fun typeOn(candidate: Function) = candidate.parameters[index]
        internal fun closureTypeOn(candidate: Function) = typeOn(candidate) as? Type.FunctionType
            ?: throw IllegalArgumentException("Invalid candidate provided")
    }

    data class ClosureParameter(val name: String, val argument: Argument, val paramIndex: Int) : Position {
        override val node get() = argument.node
        override fun typeOn(candidate: Function) = argument.closureTypeOn(candidate).parameters[paramIndex]
    }

    data class ClosureImage(val argument: Argument) : Position {
        override val node get() = argument.node
        override fun typeOn(candidate: Function) = argument.closureTypeOn(candidate).image
    }

    data class Image(override val node: ExpressionalNode) : Position {
        override fun typeOn(candidate: Function) = candidate.image
    }
}

val Position.types: List<Type> get() = node.candidates.map { typeOn(it) }

class ExpressionalNode internal constructor(
    prototype: ExpressionalNodePrototype,
    hydrator: EntityHydrator,
) {
    init {
        hydrator.register(prototype, this)
    }

    val call = prototype.expression

    val candidates = prototype.candidates.toMutableList()
    val positions = hydrator.hydrate(prototype.positions, this)
    val image = positions.last() as? Position.Image ?: throw IllegalStateException("Last position must be image")
    val constraints = hydrator.hydrate(prototype.constraints)
}

class EntityHydrator {
    private val hydratedPositions = IdentityHashMap<PositionPrototype, Position>()
    private val hydratedExpressions = IdentityHashMap<ExpressionalNodePrototype, ExpressionalNode>()
    private val hydratedConstraints = HashMap<Pair<PositionPrototype, PositionPrototype>, Constraint>()

    fun hydrate(positions: Iterable<PositionPrototype>, node: ExpressionalNode) = positions.map { hydrate(it, node) }

    private fun hydrate(prototype: PositionPrototype, node: ExpressionalNode) =
        prototype.manufacture(node, this).also { hydratedPositions[prototype] = it }

    fun cached(argument: Argument, node: ExpressionalNode) =
        hydratedPositions.getOrPut(argument) { hydrate(argument, node) } as Position.Argument

    fun hydrate(constraints: Iterable<ConstraintPrototype>) =
        constraints.map { (head, tail) ->
            if (head to tail in hydratedConstraints) return@map hydratedConstraints[head to tail]!!
            ensurePresence(tail.node ?: throw IllegalStateException("Can not hydrate unfinished position"))
            ensurePresence(head.node ?: throw IllegalStateException("Can not hydrate unfinished position"))
            Constraint(
                hydratedPositions[head] ?: throw IllegalStateException("Position must be hydrated if node is hydrated"),
                hydratedPositions[tail] ?: throw IllegalStateException("Position must be hydrated if node is hydrated"),
            )
        }

    private fun ensurePresence(node: ExpressionalNodePrototype) {
        if (node !in hydratedExpressions) node.manufacture(this)
    }

    fun register(prototype: ExpressionalNodePrototype, node: ExpressionalNode) {
        hydratedExpressions[prototype] = node
    }
}

data class Graph(val rootNode: ExpressionalNode) {
    val allNodes = HashSet<ExpressionalNode>().also { addAllNodes(rootNode, it) }

    private fun addAllNodes(node: ExpressionalNode, allNodes: MutableSet<ExpressionalNode>) {
        if (allNodes.add(node))
            node.constraints.forEach { (head, tail) ->
                addAllNodes(head.node, allNodes)
                addAllNodes(tail.node, allNodes)
            }
    }

    val constraints get() = allNodes.flatMapTo(HashSet()) { it.constraints }
}

data class Constraint(val tail: Position, val head: Position) {
    private fun satisfyTail(): Boolean =
        tail.node.candidates.filtering { candidate -> head.types.any { tail.typeOn(candidate) isAssignableFrom it } }

    private fun satisfyHead(): Boolean =
        head.node.candidates.filtering { candidate -> tail.types.any { it isAssignableFrom head.typeOn(candidate) } }

    @OptIn(ExperimentalStdlibApi::class)
    fun satisfyAndGiveAllSiblingsThatMightBeUnsatisfied() = buildSet {
        if (satisfyHead()) addAll(head.node.constraints)
        if (satisfyTail()) addAll(tail.node.constraints)
        remove(this@Constraint)
    }
}

private fun <E> MutableList<E>.filtering(function: (E) -> Boolean): Boolean {
    val initialSize = size
    iterator().run {
        while (hasNext()) {
            if (!function(next()))
                remove()
        }
    }
    return initialSize != size
}

class ConstraintGraphBuilder(private val context: Context) {

    private class ConstraintBuilder {
        val variables = Stack<ClosureParameter>()

        fun paramFor(variable: Expression.Variable): ClosureParameter =
            variables.lastOrNull { it.name == variable.name }
                ?: throw IllegalStateException("Variable '${variable.name}' does not exist")

        inline fun scoped(params: List<ClosureParameter>, processor: ConstraintBuilder.() -> Unit) {
            variables.addAll(params)
            processor(this)
            repeat(params.size) { variables.pop() }
        }
    }

    fun buildFrom(expression: Expression.FunctionCall) =
        Graph(buildFrom(expression, ConstraintBuilder()).manufacture(EntityHydrator()))

    private fun buildFrom(
        call: Expression.FunctionCall,
        constraintBuilder: ConstraintBuilder
    ): ExpressionalNodePrototype {
        val constraints = mutableListOf<ConstraintPrototype>()
        val positions = call.arguments.flatMapIndexed { i, argument ->
            Argument(i).let { argPosition ->
                when (argument) {
                    is Expression.Closure.Filled -> {
                        val parameters = argument.parameters.mapIndexed { i, param ->
                            ClosureParameter(param.name, argPosition, i)
                        }
                        val closureImage = ClosureImage(argPosition)
                        constraintBuilder.scoped(parameters) {
                            constraints += when (argument.expression) {
                                is Expression.FunctionCall -> ConstraintPrototype(
                                    buildFrom(argument.expression, this).image,
                                    closureImage,
                                )
                                is Expression.Variable -> ConstraintPrototype(
                                    paramFor(argument.expression),
                                    closureImage,
                                )
                                is Expression.Closure -> throw UnsupportedOperationException("Closures are not supported as top-level expressions")
                            }
                        }
                        parameters + closureImage
                    }
                    is Expression.Closure.Empty -> listOf(ClosureImage(argPosition))
                    is Expression.FunctionCall -> listOf(argPosition).also {
                        constraints += ConstraintPrototype(
                            argPosition,
                            buildFrom(argument, constraintBuilder).image
                        )
                    }
                    is Expression.Variable -> listOf(argPosition).also {
                        constraints += ConstraintPrototype(constraintBuilder.paramFor(argument), argPosition)
                    }
                }
            }
        } + Image()
        return ExpressionalNodePrototype(
            call,
            context.findFunctionsBy(
                call.name,
                call.arguments.size,
                call.arguments.filterIsInstance<Expression.Closure>().mapIndexed { i, arg ->
                    Context.ClosureParameterCount(i, arg.parameters.size)
                }
            ).toMutableList(),
            positions,
            constraints.toList(),
        ).also { node -> positions.forEach { it.node = node } }
    }
}
