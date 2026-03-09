package net.explorviz.span.adapter.span.service.validation

import io.opentelemetry.proto.trace.v1.Span
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.file.Paths
import java.time.DateTimeException
import net.explorviz.span.adapter.service.converter.AttributesReader
import net.explorviz.span.adapter.service.TokenService
import net.explorviz.span.adapter.service.validation.SpanValidator
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@ApplicationScoped
class DefaultSpanValidator
@Inject constructor(
    private val tokenService: TokenService,
    @ConfigProperty(name = "explorviz.validate.token-existence") var validateTokens: Boolean = false
) : SpanValidator {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultSpanValidator::class.java)
        private const val MIN_DEPTH_FQN_NAME = 2 // Method FQN must at least include class / file if no file path is set
    }

    override fun isValid(span: Span): Boolean {
        val attr = AttributesReader(span)
        return validateTimestamp(span.startTimeUnixNano) && validateTimestamp(span.endTimeUnixNano) && isValid(attr)
    }

    fun isValid(spanAttributes: AttributesReader): Boolean {
        return validateToken(
            spanAttributes.landscapeToken,
            spanAttributes.secret,
        ) && validateHost(
            spanAttributes.hostName,
            spanAttributes.hostIpAddress,
        ) && validateApp(spanAttributes.applicationName, spanAttributes.applicationLanguage) && validateOperation(
            spanAttributes.methodFqn, spanAttributes.filePath,
        ) && validateK8s(spanAttributes)
    }

    private fun validateToken(token: String?, givenSecret: String?): Boolean {
        val hasTokenAndSecret = !token.isNullOrBlank() && !givenSecret.isNullOrBlank()

        if (token.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No or blank token.")
        }

        if (givenSecret.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No or blank secret.")
        }

        var isValid = true
        if (hasTokenAndSecret && validateTokens) {
            isValid = tokenService.validLandscapeTokenValueAndSecret(token!!, givenSecret!!)
            if (!isValid) {
                LOGGER.trace("Invalid span: Token and/or secret are unknown.")
            }
        }

        return hasTokenAndSecret && isValid
    }

    private fun validateTimestamp(timestamp: Long): Boolean {
        return try {
            if (timestamp <= 0L) {
                throw NumberFormatException("Time must be positive")
            }
            true
        } catch (e: DateTimeException) {
            LOGGER.trace("Invalid span timestamp: Date time exception - ${e.message}")
            false
        } catch (e: NumberFormatException) {
            LOGGER.trace("Invalid span timestamp: Number format exception - ${e.message}")
            false
        }
    }

    private fun validateHost(hostName: String?, hostIp: String?): Boolean {
        val isValid = !hostName.isNullOrBlank() && !hostIp.isNullOrBlank()

        if (hostName.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No hostname.")
        }

        if (hostIp.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No IP address.")
        }

        return isValid
    }

    private fun validateApp(appName: String?, appLang: String?): Boolean {
        val isValid = !appName.isNullOrBlank() && !appLang.isNullOrBlank()

        if (appName.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No application name.")
        }

        if (appLang.isNullOrBlank()) {
            LOGGER.trace("Invalid span: No application language given.")
        }

        return isValid
    }

    private fun validateOperation(fqn: String, filePath: String): Boolean {
        // Despite OTel semconv, absolute file paths are not desirable as we don't know where the application begins
        val filePathIsValid =
            filePath.isNotBlank() && runCatching { Paths.get(filePath).isAbsolute }.getOrDefault(false)

        val operationFqnSplit = fqn.split(".")

        if (filePathIsValid) {
            if (operationFqnSplit.last().isBlank()) {
                LOGGER.trace("Invalid span: Invalid operation name {}", fqn)
                return false
            }
            return true
        } else {
            if (operationFqnSplit.size < MIN_DEPTH_FQN_NAME) {
                LOGGER.trace(
                    "Invalid span: Operation name \"{}\" too short with invalid file path given: \"{}\"",
                    fqn,
                    filePath,
                )
                return false
            }
            if (operationFqnSplit.any { it.isBlank() }) {
                LOGGER.trace(
                    "Invalid span: Operation name \"{}\" contains empty segments with invalid file path given: \"{}\"",
                    fqn,
                    filePath,
                )
                return false
            }
        }

        return true
    }

    private fun validateK8s(spanAttributes: AttributesReader): Boolean {
        val hasPodName = spanAttributes.k8sPodName.isNotEmpty()
        val hasNamespace = spanAttributes.k8sNamespace.isNotEmpty()
        val hasNodeName = spanAttributes.k8sNodeName.isNotEmpty()
        val hasDeployment = spanAttributes.k8sDeploymentName.isNotEmpty()

        val hasAll = hasPodName && hasNamespace && hasNodeName && hasDeployment
        val hasNone = !hasPodName && !hasNamespace && !hasNodeName && !hasDeployment

        return hasAll || hasNone
    }
}
