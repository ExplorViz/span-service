package net.explorviz.span.timestamp;

import org.neo4j.driver.types.Node;

public record Timestamp(long epochMilli, long spanCount) {
  public static Timestamp fromNode(final Node node) {
    final long tenSecondEpoch = node.get("tenth_second_epoch").asLong();
    final long spanCount = node.get("span_count").asLong();

    return new Timestamp(tenSecondEpoch, spanCount);
  }
}
