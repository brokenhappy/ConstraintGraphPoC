package multiExecutor

import javax.inject.Inject

class DefaultExecutor @Inject constructor() : Executor {
    override fun execute(executable: Executor.Executable) {
        executable.onStart()
        executable.execute()
        executable.onEnd()
    }
}
