package multiExecutor

interface Executor {
    /** Assume all functions are called from worker threads */
    interface Executable {
        fun onStart()
        fun execute()
        fun onEnd()
    }

    fun execute(executable: Executable)
}