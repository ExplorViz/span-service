package net.explorviz.span.timestamp;

import org.neo4j.driver.Record;

public record Timestamp(long epochMilli, long spanCount) {
  public static Timestamp fromRecord(final Record record) {
    final long tenSecondEpoch = record.get("tenth_second_epoch").asLong();
    final long spanCount = record.get("span_count").asLong();

    return new Timestamp(tenSecondEpoch, spanCount);
  }
}
