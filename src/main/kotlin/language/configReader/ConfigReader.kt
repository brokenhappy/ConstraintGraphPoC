package language.configReader

interface ConfigReader {
    data class SyntaxError(val row: Int, val column: Int, val error: String) :
        Exception("SyntaxError on $row:$column '$error'")

    fun read(config: Iterator<String>): Context
}
fun ConfigReader.read(config: String) = read(config.lineSequence().iterator())