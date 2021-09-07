package language.codeHighlighter

import dagger.Component

@Component
interface CodeHighlighterComponent {
    fun getKotlinInstance(): SwiftyCodeHighlighter
}