package language.codeHighlighter

import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Contract
import java.awt.Color
import javax.inject.Inject

class SwiftyCodeHighlighter @Inject constructor() : CodeHighlighter {
    private val highlighter = KeywordBasedCodeHighlighter(mapOf(
        "type" to Color.ORANGE,
        "func" to Color.ORANGE,
    ))

    @Contract(pure = true)
    override fun highlight(@Language("kts") code: String) = highlighter.highlight(code)
}