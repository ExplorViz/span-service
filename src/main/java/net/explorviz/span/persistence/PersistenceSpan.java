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
    String hostName,
    String applicationName,
    String applicationLanguage,
    String applicationInstance,
    String methodFqn,
    String methodHash,
    String k8sPodName,
    String k8sNodeName,
    String k8sNamespace,
    String k8sDeploymentName
) {

  public static final UUID DEFAULT_UUID = UUID.fromString("7cd8a9a7-b840-4735-9ef0-2dbbfa01c039");
}
