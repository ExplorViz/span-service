package net.explorviz.span.trace;

import java.util.UUID;
import org.neo4j.driver.types.Node;

public record Span(
    //UUID landscapeToken, // TODO: Deviation from frontend, expects `String landscapeToken`
    //String traceId,
    String spanId,
    String parentSpanId,
    long startTime,
    long endTime,
    String methodHash
) {

  public static Span fromNode(final Node node) {
    // final UUID landscapeToken = UUID.fromString(record.get("landscape_token").asString());
    // final String traceId = record.get("trace_id").asString();
    final String spanId = node.get("span_id").asString();
    final String parentSpanId = node.get("parent_span_id").asString();
    final long startTime = node.get("start_time").asLong();
    final long endTime = node.get("end_time").asLong();
    final String methodHash = node.get("method_hash").asString();

    return new Span(spanId, parentSpanId, startTime, endTime, methodHash);
  }
}
