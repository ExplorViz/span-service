package net.explorviz.span.persistence;

import java.util.UUID;

public record PersistenceSpan(
    UUID landscapeToken,
    String gitCommitChecksum,
    String spanId,
    String parentSpanId,
    String traceId,
    long startTime,
    long endTime,
    String nodeIpAddress, // TODO: Convert into InetAddress type?
    String applicationName,
    String applicationLanguage,
    int applicationInstance,
    String methodFqn,
    String methodHash
) {

  public static final UUID DEFAULT_UUID = UUID.fromString("7cd8a9a7-b840-4735-9ef0-2dbbfa01c039");
}
