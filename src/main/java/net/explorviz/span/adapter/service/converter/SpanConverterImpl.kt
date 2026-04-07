package net.explorviz.span.adapter.service.converter

import jakarta.enterprise.context.ApplicationScoped
import java.util.*
import net.explorviz.span.adapter.service.converter.fqn.DefaultParser
import net.explorviz.span.adapter.service.converter.fqn.FqnParser
import net.explorviz.span.adapter.service.converter.fqn.JavaFqnParser
import net.explorviz.span.persistence.PersistenceSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Converts a [io.opentelemetry.proto.trace.v1.Span] to a [PersistenceSpan]. */
@ApplicationScoped
class SpanConverterImpl : SpanConverter<PersistenceSpan> {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SpanConverterImpl::class.java)

        private val JAVA_PARSER = JavaFqnParser()
        private val DEFAULT_PARSER = DefaultParser()

        private val PARSERS = mapOf(
            "java" to JAVA_PARSER,
        )

        fun getFqnParser(language: String): FqnParser {
            return PARSERS[language.lowercase()] ?: DEFAULT_PARSER
        }
    }

    override fun fromOpenTelemetrySpan(ocSpan: io.opentelemetry.proto.trace.v1.Span): PersistenceSpan {

        val attributesReader = AttributesReader(ocSpan)

        val gitCommitChecksum = attributesReader.gitCommitChecksum
        val spanId = IdHelper.convertSpanId(ocSpan.spanId.toByteArray())
        val traceId = IdHelper.convertTraceId(ocSpan.traceId.toByteArray())
        val startTime = ocSpan.startTimeUnixNano
        val endTime = ocSpan.endTimeUnixNano
        val nodeIpAddress = attributesReader.hostIpAddress
        val hostName = attributesReader.hostName
        val applicationName = attributesReader.applicationName
        val applicationLanguage = attributesReader.applicationLanguage
        val applicationInstance = attributesReader.applicationInstanceId
        val filePathAttrib = attributesReader.filePath
        val methodFqn = attributesReader.methodFqn
        val k8sPodName = attributesReader.k8sPodName
        val k8sNodeName = attributesReader.k8sPodName
        val k8sNamespace = attributesReader.k8sNamespace
        val k8sDeploymentName = attributesReader.k8sDeploymentName

        val landscapeToken = attributesReader.landscapeToken;

        val parentSpanId = if (ocSpan.parentSpanId.size() > 0) {
            IdHelper.convertSpanId(ocSpan.parentSpanId.toByteArray())
        } else {
            ""
        }

        val parsingResult: FqnParser.ParsingResult = getFqnParser(applicationLanguage).parseFunctionFqn(methodFqn)

        // Explicit file path attribute takes precedence. If unset, then parse from fqn
        val filePath =
            if (filePathAttrib != DefaultAttributeValues.DEFAULT_FILE_PATH) filePathAttrib else parsingResult.filePath

        val functionName = parsingResult.functionName
        val className = parsingResult.className

        return PersistenceSpan(
            landscapeToken,
            gitCommitChecksum,
            spanId,
            parentSpanId,
            traceId,
            startTime,
            endTime,
            nodeIpAddress,
            hostName,
            applicationName,
            applicationLanguage ?: PersistenceSpan.DEFAULT_LANGUAGE,
            applicationInstance,
            filePath,
            functionName,
            className,
            k8sPodName,
            k8sNodeName,
            k8sNamespace,
            k8sDeploymentName,
        )
    }
}
