package language.configReader

import dagger.Component

@Component
interface ScriptExecutorComponent {
    fun getSwiftInstance(): SwiftyConfigReader
}