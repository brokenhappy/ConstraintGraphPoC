package constraintGraphBuilder

import constraintGraphBuilder.PositionPrototype.*
import javafx.scene.paint.Color
import javafx.scene.text.Text
import language.configReader.Context
import tornadofx.style
import typeResolve.Expression
import typeResolve.GraphSatisfactionEventHandler
import typeResolve.GraphSatisfactionEventHandler.MatrixCell
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
    allNodes: Stack<ExpressionalNodePrototype>,
) {
    val positions: List<Position>

    init {
        hydrator.register(prototype, this)
        positions = hydrator.hydrate(prototype.positions, this)
        if (allNodes.isNotEmpty())
            allNodes.pop().manufacture(hydrator, allNodes)
    }

    val call = prototype.expression
    val candidates = prototype.candidates.toMutableList()
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
            Constraint(
                hydratedPositions[head] ?: throw IllegalStateException("Position must be hydrated if node is hydrated"),
                hydratedPositions[tail] ?: throw IllegalStateException("Position must be hydrated if node is hydrated"),
            )
        }

    fun register(prototype: ExpressionalNodePrototype, node: ExpressionalNode) {
        hydratedExpressions[prototype] = node
    }
}

data class Graph(val rootNode: ExpressionalNode) {

    val allSortedNodes: List<ExpressionalNode>

    init {
        fun addAllNodes(node: ExpressionalNode, allNodes: MutableSet<ExpressionalNode>) {
            if (allNodes.add(node))
                node.constraints.forEach { (head, tail) ->
                    addAllNodes(head.node, allNodes)
                    addAllNodes(tail.node, allNodes)
                }
        }

        val allNodes = HashSet<ExpressionalNode>().also { addAllNodes(rootNode, it) }
        val allCallsByNode = allNodes.associateWithTo(IdentityHashMap()) { it.call }
        fun Expression.FunctionCall.findDepthOf(call: Expression.FunctionCall, currentDepth: Int): Int {
            fun recurseOn(expression: Expression, currentDepth: Int): Int = when (expression) {
                Expression.Closure.Empty,
                is Expression.Variable -> 0
                is Expression.Closure.Filled -> recurseOn(expression.expression, currentDepth)
                is Expression.FunctionCall -> expression.findDepthOf(call, currentDepth + 1)
            }
            return if (this === call)
                currentDepth
            else
                arguments.maxOfOrNull { argument -> recurseOn(argument, currentDepth) } ?: 0
        }

        fun findDepth(node: ExpressionalNode): Int = allCallsByNode[node]!!.let { call ->
            allCallsByNode[rootNode]!!.findDepthOf(call, 0)
        }
        allSortedNodes = allNodes.sortedBy { findDepth(it) }
    }

    private val nodeLabels: Map<ExpressionalNode, String>

    init {
        val usedNames = mutableSetOf<String>()
        nodeLabels = allSortedNodes.associateWith { node ->
            node.call.name.let { name ->
                if (name !in usedNames) name
                else (0..9999).firstNotNullOf { i -> "name$i".takeUnless { it in usedNames } }
            }
        }
    }

    private val ExpressionalNode.label get() = nodeLabels[this]!!

    private val positionLabels: Map<Position, String> = allSortedNodes
        .flatMap { it.positions }
        .withIndex()
        .associate { (i, position) -> position to "p$i" }

    fun labelOf(position: Position) = positionLabels[position]!!

    data class Matrix(val columns: List<List<Text>>) {
        init {
            val columnWidths = columns.map { column -> column.sumOf { it.text.length } }
            columnWidths.zip(columns).forEach { (columWidth, column) ->
                column.forEach { text ->
                    text.text = " ".repeat(columWidth - text.text.length) + text.text
                }
            }
        }

        val rows = columns.first().indices.map { x -> columns.indices.map { y -> columns[y][x] } }
    }

    fun represent(highlightedCells: Set<MatrixCell>): List<Matrix> {
        val highlighterCellByPosition = highlightedCells.associateBy { it.position }
        return allSortedNodes.map { node ->
            Matrix(
                listOf(listOf(Text(node.label)) + node.candidates.indices.map { Text() }) +
                        node.positions.map { position ->
                            listOf(Text(labelOf(position)).also {
                                if (position in highlighterCellByPosition)
                                    it.style { fill = Color.GREEN }
                            }) +
                                    node.candidates.map { candidate ->
                                        Text(position.typeOn(candidate).toString()).also { text ->
                                            highlighterCellByPosition[position]?.also { (highlightedPosition, highlightedCandidate) ->
                                                if (highlightedPosition == position) {
                                                    text.style {
                                                        fill = if (highlightedCandidate == candidate)
                                                            Color.BLUE
                                                        else
                                                            Color.GREEN
                                                    }
                                                }
                                            }
                                        }
                                    }
                        })
        }
    }

    val constraints get() = allSortedNodes.flatMapTo(HashSet()) { it.constraints }
}

data class Constraint(val tail: Position, val head: Position) {
    private suspend fun satisfyTail(eventHandler: GraphSatisfactionEventHandler): Boolean =
        tail.node.candidates.filtering { tailCandidate ->
            val smaller = MatrixCell(tail, tailCandidate)
            head.node.candidates.any { headCandidate ->
                val greater = MatrixCell(head, headCandidate)
                eventHandler.handleTypeCheck(smaller, greater)
                smaller.type.isAssignableFrom(greater.type)
                    .also { if (it) eventHandler.handleMatch(smaller, greater) }
            }.also { if (!it) eventHandler.handleElimination(smaller, head) }
        }

    private suspend fun satisfyHead(eventHandler: GraphSatisfactionEventHandler): Boolean =
        head.node.candidates.filtering { headCandidate ->
            val greater = MatrixCell(head, headCandidate)
            tail.node.candidates.any { tailCandidate ->
                val smaller = MatrixCell(tail, tailCandidate)
                eventHandler.handleTypeCheck(greater, smaller)
                smaller.type.isAssignableFrom(greater.type)
                    .also { if (it) eventHandler.handleMatch(smaller, greater) }
            }.also { if (!it) eventHandler.handleElimination(greater, head) }
        }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun satisfyAndGiveAllSiblingsThatMightBeUnsatisfied(eventHandler: GraphSatisfactionEventHandler) =
        buildSet {
            if (satisfyHead(eventHandler)) addAll(head.node.constraints)
            if (satisfyTail(eventHandler)) addAll(tail.node.constraints)
            remove(this@Constraint)
        }
}

private inline fun <E> MutableList<E>.filtering(function: (E) -> Boolean): Boolean {
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
        Graph(buildFrom(expression, ConstraintBuilder(), mutableListOf()).manufacture(EntityHydrator()))

    private fun buildFrom(
        call: Expression.FunctionCall,
        constraintBuilder: ConstraintBuilder,
        allNodes: MutableList<ExpressionalNodePrototype>,
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
                                    buildFrom(argument.expression, this, allNodes).image,
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
                            buildFrom(argument, constraintBuilder, allNodes).image,
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
            allNodes,
        ).also(allNodes::add)
    }
}
