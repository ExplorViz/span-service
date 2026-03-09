package net.explorviz.span.adapter.service.converter.fqn

/**
 * Parses Java method fqn as specified by
 * [OTel semantic conventions](https://opentelemetry.io/docs/specs/semconv/registry/attributes/code/).
 *
 * Attempts to detect inner classes if multiple fqn segments are capitalized, e.g.
 * `net.explorviz.app.MyClass.MyInnerClass.doSomething` would result in class name `MyClass.MyInnerClass`.
 *
 * Example fqn: `"net.explorviz.app.MyConverter.convert"`
 * Example result: `["net/explorviz/app/MyConverter.java", "convert", "MyConverter"]`
 */
class JavaFqnParser : FqnParser {
    companion object {
        const val FILE_EXTENSION = ".java"
    }

    override fun parseFunctionFqn(functionFqn: String): FqnParser.ParsingResult {
        // Ignore lambda portion. If we explicitly want to support this, the data model would need to allow functions to
        // have child functions. For now, we simplify and treat this as a call of the containing function.
        val fqnWithoutLambda = functionFqn.substringBefore("$")
        val separatedFqn = fqnWithoutLambda.split(".")

        val lastPackageIndex = separatedFqn.dropLast(1).indexOfLast { it.firstOrNull()?.isUpperCase() != true }
        val fileNameIndex = lastPackageIndex + 1

        val filePath = separatedFqn.take(fileNameIndex + 1).joinToString("/") + FILE_EXTENSION
        val className = separatedFqn.subList(fileNameIndex, separatedFqn.lastIndex).joinToString(".")
        val methodName = separatedFqn.last()

        return FqnParser.ParsingResult(filePath, methodName, className)
    }
}
