package net.explorviz.span.adapter.service.converter

import io.opentelemetry.proto.trace.v1.Span

/**
 * Converts OpenTelemetry spans into another type.
 *
 * @param <T> the type spans are converted to.
 */
interface SpanConverter<T> {

    /** Converts an OpenTelemetry {@link Span} into {@link T}. */
    fun fromOpenTelemetrySpan(ocSpan: Span): T
}
