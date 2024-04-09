package net.explorviz.span.trace;

import com.datastax.oss.driver.api.core.cql.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Trace(
    UUID landscapeToken,
    String traceId,
    String gitCommitChecksum,
    long startTime,
    long endTime,
    long duration, // TODO: Pointless?
    int overallRequestCount, // TODO: Always 1 for backwards compat for now
    int traceCount, // TODO: Always 1 for backwards compat for now
    List<Span> spanList
) {

  public static Trace fromRow(final Row row) {
    final UUID landscapeToken = row.getUuid("landscape_token");
    final String traceId = row.getString("trace_id");
    final String gitCommitChecksum = row.getString("git_commit_checksum");
    // TODO: Remove millisecond/nanosecond mismatch hotfix
    final long startTime = row.getLong("start_time");
    final long endTime = row.getLong("end_time");
    final long duration = endTime - startTime;
    final int overallRequestCount = 1;
    final int traceCount = 1;
    final List<Span> spanList = new ArrayList<>();

    return new Trace(landscapeToken, traceId, gitCommitChecksum, startTime, endTime, duration,
        overallRequestCount,
        traceCount, spanList);
  }

  /*public static Trace fromSpanList(final List<Span> spans) {
    final Optional<Span> root = spans.stream().filter(span -> span.parentSpanId().isEmpty())
        .findAny();
    if (root.isEmpty()) {
      throw new IllegalArgumentException("No root span found in span list");
    }
    final Span span = root.get();

    final UUID landscapeToken = span.landscapeToken();
    final String traceId = span.traceId();
    final long startTime = span.startTime();
    final long endTime = span.endTime();
    final long duration = endTime - startTime;
    final int overallRequestCount = 1;
    final int traceCount = 1;
    // TODO: Trace should not contain itself? i.e. filter out parent_span_id = 0
    final List<Span> spanList = Collections.unmodifiableList(spans);

    return new Trace(landscapeToken, traceId, startTime, endTime, duration, overallRequestCount,
        traceCount, spanList);
  }*/
}
