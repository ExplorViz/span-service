package net.explorviz.span.adapter.service.converter.fqn

/**
 * Fallback generic parser. Assumes dot-separated string, where the last segment represents the function name and
 * the prior segments represent the file's fqn (e.g. "src/main/SomeFile/myFunc"). Does not add any file extension to
 * the file fqn. Assumes no classes are included in the fqn.
 */
class DefaultParser : FqnParser {

    override fun parseFunctionFqn(functionFqn: String): FqnParser.ParsingResult {
        val separatedFqn = functionFqn.split(".")
        val fileFqn = separatedFqn.dropLast(1).joinToString(".")
        val functionName = separatedFqn.last()
        return FqnParser.ParsingResult(fileFqn, functionName);
    }
}
