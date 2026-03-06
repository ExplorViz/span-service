package net.explorviz.span.adapter.service.converter

import jakarta.enterprise.context.ApplicationScoped
import java.util.*
import net.explorviz.span.hash.HashHelper.calculateSpanHash
import net.explorviz.span.persistence.PersistenceSpan
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Converts a [io.opentelemetry.proto.trace.v1.Span] to a [PersistenceSpan]. */
@ApplicationScoped
class SpanConverterImpl : SpanConverter<PersistenceSpan> {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SpanConverterImpl::class.java)
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
        val methodFqn = attributesReader.methodFqn
        val k8sPodName = attributesReader.k8sPodName
        val k8sNodeName = attributesReader.k8sPodName
        val k8sNamespace = attributesReader.k8sNamespace
        val k8sDeploymentName = attributesReader.k8sDeploymentName

        val landscapeToken = when (attributesReader.landscapeToken) {
            "mytokenvalue" -> PersistenceSpan.DEFAULT_UUID
            else -> runCatching { UUID.fromString(attributesReader.landscapeToken) }.onFailure {
                LOGGER.error(
                    "Invalid landscape token: {}",
                    attributesReader.landscapeToken,
                )
            }.getOrDefault(PersistenceSpan.DEFAULT_UUID)
        }

        val parentSpanId = if (ocSpan.parentSpanId.size() > 0) {
            IdHelper.convertSpanId(ocSpan.parentSpanId.toByteArray())
        } else {
            ""
        }

        val methodHashCode = calculateSpanHash(
            landscapeToken, nodeIpAddress,
            applicationName, applicationInstance, methodFqn, k8sPodName, k8sNodeName, k8sNamespace,
            k8sDeploymentName,
        )

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
            applicationLanguage,
            applicationInstance,
            methodFqn,
            methodHashCode,
            k8sPodName,
            k8sNodeName,
            k8sNamespace,
            k8sDeploymentName,
        );
    }
}
