package view

import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.layout.Priority
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import language.configReader.ConfigReader
import language.configReader.SwiftyConfigReader
import language.configReader.read
import language.errorAnalyzer.ErrorAnalyzer.ErrorPart
import multiExecutor.DaggerMultiExecutorComponent
import multiExecutor.Executor
import tornadofx.*
import typeResolve.Expression
import typeResolve.ExpressionParser
import typeResolve.TypeResolver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class EditorView : View() {
    private val multiExecutor = DaggerMultiExecutorComponent.create().getInstance()
    private val expressionParser = ExpressionParser()

    private var currentExpression: Expression? = null
    private lateinit var output: TextFlow
    private lateinit var expressionInput: TextField
    private lateinit var executeButton: Button
    private lateinit var confirmButton: Button
    private lateinit var configInput: TextArea

    private val isWaitingForConfirmation = AtomicBoolean(false)
    private val stepTime = AtomicInteger(300)
    private val shouldHaltForEventsView = GraphSatisfactionVisualizingEventHandler.ShouldHaltForEventsView(
        shouldHaltForStart = AtomicBoolean(false),
        shouldHaltForStartingNewConstraint = AtomicBoolean(false),
        shouldHaltForTypeCheck = AtomicBoolean(false),
        shouldHaltForElimination = AtomicBoolean(true),
        shouldHaltForMatch = AtomicBoolean(true)
    )

    override val root = splitpane(Orientation.VERTICAL) {
        style {
            font = Font.font(java.awt.Font.MONOSPACED, 14.0)
        }
        splitpane(Orientation.VERTICAL) {
            setDividerPosition(0, 0.9)
            configInput = textarea(
                """
                    type Int
                    type String
                    type MutableString: String
                    
                    func foo((Int) -> Int) -> Int
                    func foo((String) -> String) -> String
                    
                    func bar() -> MutableString
                    
                    func +(Int, Int) -> Int
                    func +(String, String) -> String
                """.trimIndent()
            )
            expressionInput = textfield("foo { \$0 + bar() }") {
                textProperty().addListener { _, _, expressionString ->
                    currentExpression = try {
                        expressionParser.parse(expressionString)
                    } catch (e: ExpressionParser.SyntaxError) {
                        null
                    }
                }
            }.also { it.prefWidth = width }
        }
        splitpane {
            setDividerPosition(0, 0.2)
            vbox {
                hbox {
                    executeButton = button("Resolve!") { setOnMouseClicked { runScript() } }
                    confirmButton = button("Continue") {
                        isDisable = true
                        setOnMouseClicked {
                            isWaitingForConfirmation.set(false)
                            isDisable = true
                        }
                    }
                }
                text("Should halt for:")
                hbox {
                    label("start")
                    spacer { hgrow = Priority.ALWAYS }
                    checkbox { bind(shouldHaltForEventsView.shouldHaltForStart) }
                }
                hbox {
                    label("starting new constraint")
                    spacer { hgrow = Priority.ALWAYS }
                    checkbox { bind(shouldHaltForEventsView.shouldHaltForStartingNewConstraint) }
                }
                hbox {
                    label("type check")
                    spacer { hgrow = Priority.ALWAYS }
                    checkbox { bind(shouldHaltForEventsView.shouldHaltForTypeCheck) }
                }
                hbox {
                    label("elimination")
                    spacer { hgrow = Priority.ALWAYS }
                    checkbox { bind(shouldHaltForEventsView.shouldHaltForElimination) }
                }
                hbox {
                    label("match")
                    spacer { hgrow = Priority.ALWAYS }
                    checkbox { bind(shouldHaltForEventsView.shouldHaltForMatch) }
                }

                val stepTimeLabel = text()
                hbox {
                    label("0s")
                    spacer { hgrow = Priority.ALWAYS }
                    label("2s")
                }
                slider((0..2000)) {
                    valueProperty().onChange { newValue ->
                        stepTime.set(newValue.roundToInt().also { stepTimeLabel.text = "Step time: ${it}ms" })
                    }
                    valueProperty().set(300.0)
                }
                hbox {
                    button("preset 1") {
                        setOnMouseClicked {
                            configInput.text = """
                                type A
                                type B
                                type C
                                type D
                                type E

                                func abcd() -> A
                                func abcd() -> B
                                func abcd() -> C
                                func abcd() -> D

                                func cde(C) -> C
                                func cde(D) -> D
                                func cde(E) -> E

                                func +(D, D) -> D
                                func +(B, B) -> B
                                func +(E, E) -> E
                            """.trimIndent()
                            expressionInput.text = "cde(abcd()) + abcd()"
                        }
                    }
                }
            }
            scrollpane {
                output = textflow()
            }
        }
    }

    private fun CheckBox.bind(atomicBoolean: AtomicBoolean) {
        isSelected = atomicBoolean.get()
        selectedProperty().onChange(atomicBoolean::set)
    }

    private val runProcess = object : Executor.Executable {
        override fun onStart() = Platform.runLater {
            executeButton.isDisable = true
            expressionInput.isDisable = true
            output.clear()
        }

        override fun execute(): Unit = runBlocking {
            launch {
                try {
                    TypeResolver(SwiftyConfigReader().read(configInput.text))
                        .resolveFreely(
                            expressionParser.parse(expressionInput.text),
                            GraphSatisfactionVisualizingEventHandler (
                                visualizeEvent = {
                                    Platform.runLater {
                                        output.children.clear()
                                        output.children += it
                                    }
                                },
                                waitForInput = {
                                    isWaitingForConfirmation.set(true)
                                    Platform.runLater {
                                        confirmButton.isDisable = false
                                    }
                                    while (isWaitingForConfirmation.get())
                                        delay(5)
                                },
                                shouldHaltForEventsView,
                                stepTime,
                            ),
                        )
                } catch (err: ExpressionParser.SyntaxError) {
                    Platform.runLater {
                        output.clear()
                        output.children += Text("Expression syntax error on ")
                        output.children += Hyperlink(err.column.toString()).apply {
                            textFill = Color.BLUE
                            setOnMouseClicked {
                                expressionInput.requestFocus()
                                expressionInput.positionCaret(err.column)
                            }
                        }
                        output.children += Text(": '${err.message}'")
                    }
                } catch (err: ConfigReader.SyntaxError) {
                    Platform.runLater {
                        output.clear()
                        output.children += Text("Syntax error on ")
                        output.children += Hyperlink("${err.row}:${err.column}").apply {
                            textFill = Color.BLUE
                            setOnMouseClicked {
                                configInput.setCursorAt(ErrorPart.CodeLink(err.row, err.column + 1))
                            }
                        }
                        output.children += Text(": '${err.message}'")
                    }
                }
            }
        }

        override fun onEnd() = Platform.runLater {
            executeButton.isDisable = false
            expressionInput.isDisable = false
        }
    }

    private fun TextArea.setCursorAt(link: ErrorPart.CodeLink) {
        requestFocus()
        positionCaret(link.resolveIndexIn(text))
    }

    private fun runScript() {
        Thread { multiExecutor.execute(runProcess) }.start()
    }
}