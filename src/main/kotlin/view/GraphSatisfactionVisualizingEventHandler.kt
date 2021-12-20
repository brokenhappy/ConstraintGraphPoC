package view

import constraintGraphBuilder.Constraint
import constraintGraphBuilder.Graph
import constraintGraphBuilder.Position
import javafx.scene.Node
import javafx.scene.text.Text
import kotlinx.coroutines.delay
import typeResolve.GraphSatisfactionEventHandler
import typeResolve.GraphSatisfactionEventHandler.MatrixCell
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GraphSatisfactionVisualizingEventHandler(
    private val visualizeEvent: suspend (List<Node>) -> Unit,
    private val waitForInput: suspend () -> Unit,
    private val shouldHaltForEventsView: ShouldHaltForEventsView,
    private val stepTime: AtomicInteger,
) : GraphSatisfactionEventHandler {

    data class ShouldHaltForEventsView(
        val shouldHaltForStart: AtomicBoolean,
        val shouldHaltForStartingNewConstraint: AtomicBoolean,
        val shouldHaltForTypeCheck: AtomicBoolean,
        val shouldHaltForElimination: AtomicBoolean,
        val shouldHaltForMatch: AtomicBoolean,
    )

    lateinit var graph: Graph
    lateinit var unsatisfiedConstraints: HashSet<Constraint>
    var currentWorkingConstraint: Constraint? = null

    private suspend fun unsatisfiedConstraintsRepresentation() = listOf(
        Text(unsatisfiedConstraints.joinToString(",", "[", "]") { constraintToString(it) }),
        Text("\n")
    ).also {
        delay(stepTime.get().toLong())
    }

    private fun constraintToString(it: Constraint) = graph.labelOf(it.head) + " <- " + graph.labelOf(it.tail)

    private fun graphRepresentation(highlightedCells: Set<MatrixCell>) =
        graph.represent(highlightedCells).flatMap { matrix ->
            matrix.rows.flatMap { row ->
                row.flatMap { listOf(Text("|"), it) } + Text("|\n")
            } + Text("\n")
        } + Text("\n")

    private fun currentConstraintRepresentation() = currentWorkingConstraint
        ?.let { listOf(Text(constraintToString(it) + "\n\n")) } ?: listOf()

    private fun titled(title: String) = listOf(Text("$title\n"))

    override suspend fun handleStart(graph: Graph, unsatisfiedConstraints: HashSet<Constraint>) {
        this.graph = graph
        this.unsatisfiedConstraints = unsatisfiedConstraints

        visualizeEvent(
            titled("Start!") +
            unsatisfiedConstraintsRepresentation() +
            graphRepresentation(setOf()))

        if (shouldHaltForEventsView.shouldHaltForStart.get())
            waitForInput()
    }

    override suspend fun handleStartingNewConstraint(constraint: Constraint) {
        currentWorkingConstraint = constraint
        visualizeEvent(
            titled("Take constraint from queue") +
            unsatisfiedConstraintsRepresentation() +
            currentConstraintRepresentation() +
            graphRepresentation(setOf()))
        if (shouldHaltForEventsView.shouldHaltForStartingNewConstraint.get())
            waitForInput()
    }

    override suspend fun handleTypeCheck(smaller: MatrixCell, greater: MatrixCell) {
        visualizeEvent(
            titled("Check types") +
            unsatisfiedConstraintsRepresentation() +
            currentConstraintRepresentation() +
            graphRepresentation(setOf(smaller, greater)))
        if (shouldHaltForEventsView.shouldHaltForTypeCheck.get())
            waitForInput()
    }

    override suspend fun handleElimination(unmatched: MatrixCell, position: Position) {
        visualizeEvent(
            titled("Eliminated candidate!") +
            unsatisfiedConstraintsRepresentation() +
            currentConstraintRepresentation() +
            graphRepresentation(setOf(unmatched, MatrixCell(position, unmatched.candidate))))
        if (shouldHaltForEventsView.shouldHaltForElimination.get())
            waitForInput()
    }

    override suspend fun handleMatch(smaller: MatrixCell, greater: MatrixCell) {
        visualizeEvent(
            titled("Match found!") +
            unsatisfiedConstraintsRepresentation() +
            currentConstraintRepresentation() +
            graphRepresentation(setOf(smaller, greater)))
        if (shouldHaltForEventsView.shouldHaltForMatch.get())
            waitForInput()
    }

    override suspend fun handleCompletion() {
        visualizeEvent(
            titled("Constraints satisfied!") +
            unsatisfiedConstraintsRepresentation() +
            graphRepresentation(setOf()))
    }
}