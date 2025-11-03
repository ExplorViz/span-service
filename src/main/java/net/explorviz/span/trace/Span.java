package net.explorviz.span.trace;

import java.util.UUID;
import org.neo4j.driver.Record;

public record Span(
    //UUID landscapeToken, // TODO: Deviation from frontend, expects `String landscapeToken`
    //String traceId,
    String spanId,
    String parentSpanId,
    long startTime,
    long endTime,
    String methodHash
) {

  public static Span fromRecord(final Record record) {
    // final UUID landscapeToken = UUID.fromString(record.get("landscape_token").asString());
    // final String traceId = record.get("trace_id").asString();
    final String spanId = record.get("span_id").asString();
    final String parentSpanId = record.get("parent_span_id").asString();
    final long startTime = record.get("start_time").asLong();
    final long endTime = record.get("end_time").asLong();
    final String methodHash = record.get("method_hash").asString();

    return new Span(spanId, parentSpanId, startTime, endTime, methodHash);
  }
}
