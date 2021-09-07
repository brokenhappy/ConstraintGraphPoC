package language

import language.codeHighlighter.CodeHighlighter
import language.codeHighlighter.SwiftyCodeHighlighter
import language.configReader.ConfigReader
import language.configReader.SwiftyConfigReader
import javax.inject.Inject

class LanguageFactory @Inject constructor(
    kotlinCodeHighlighter: SwiftyCodeHighlighter,
    swiftyConfigReader: SwiftyConfigReader,
) {
    enum class SupportedLanguage { SWIFTY }

    private data class LanguageImplementation(
        override val executor: ConfigReader,
        override val codeHighlighter: CodeHighlighter,
    ) : Language

    private val swifty = LanguageImplementation(swiftyConfigReader, kotlinCodeHighlighter)

    fun createFor(language: SupportedLanguage): Language = when(language) {
        SupportedLanguage.SWIFTY -> swifty
    }
}