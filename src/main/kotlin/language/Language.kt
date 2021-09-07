package language

import language.codeHighlighter.CodeHighlighter
import language.errorAnalyzer.ErrorAnalyzer
import language.configReader.ConfigReader

interface Language {
    val executor: ConfigReader
    val codeHighlighter: CodeHighlighter
}