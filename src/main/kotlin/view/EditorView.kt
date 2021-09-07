package view

import javafx.application.Platform
import javafx.geometry.Orientation
import javafx.scene.control.*
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import javafx.util.Duration
import javafx.util.converter.NumberStringConverter
import language.DaggerLanguageFactoryComponent
import language.LanguageFactory.SupportedLanguage
import language.configReader.ConfigReader
import language.configReader.SwiftyConfigReader
import language.configReader.read
import language.errorAnalyzer.ErrorAnalyzer.ErrorPart
import multiExecutor.DaggerMultiExecutorComponent
import multiExecutor.Executor
import org.jetbrains.annotations.Contract
import tornadofx.*
import typeResolve.Expression
import typeResolve.ExpressionParser
import typeResolve.TypeResolver
import view.errorOutputs.DaggerErrorOutputComponent

/**
 * I'm the least proud of this view package. The view code is the only part I did NOT write TDD and thus was the
 * most time consuming part.
 * I am aware of fallacies such as my usage of callbacks instead of passing around Observables like JavaFx is built for.
 * Frontend is NOT my strongest aspect and I kindly ask not to pay too much attention to the visuals, the
 * not-so JavaFx treatment of state and the lack of tests verifying view logic.
 *
 * I truly wish I knew how to write well-tested frontend code, especially in TDD.
 * Please, dont hesitate to give me advice and feedback, but evaluate my skills based on the "backend" ;)
 */
class EditorView : View() {
    private val multiExecutor = DaggerMultiExecutorComponent.create().getInstance()
    private val expressionParser = ExpressionParser()

    private var currentExpression: Expression? = null
    private lateinit var output: TextFlow
    private lateinit var expressionInput: TextField
    private lateinit var executeButton: Button
    private lateinit var configInput: TextArea

    override val root = splitpane(Orientation.VERTICAL) {
        style {
            font = Font.font(java.awt.Font.MONOSPACED, 14.0)
        }
        splitpane(Orientation.VERTICAL) {
            setDividerPosition(0, 0.9)
            configInput = textarea(
                """
                type Int
                
                func foo() -> Int
            """.trimIndent()
            )
            expressionInput = textfield("foo()") {
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
                executeButton = button("Resolve!") { setOnMouseClicked { runScript() } }
            }
            scrollpane {
                output = textflow()
            }
        }
    }

    private val runProcess = object : Executor.Executable {
        override fun onStart() = Platform.runLater {
            executeButton.isDisable = true
            expressionInput.isDisable = true
            output.clear()
        }

        override fun execute() = Platform.runLater {
            try {
                output.children += Text(TypeResolver(SwiftyConfigReader().read(configInput.text))
                    .resolveFreely(expressionParser.parse(expressionInput.text))
                    .rootNode.candidates.toString())
            } catch (_: ExpressionParser.SyntaxError) {
            } catch (_: ConfigReader.SyntaxError) {
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