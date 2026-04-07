package net.explorviz.span.persistence;

public record PersistenceSpan(
    String landscapeToken,
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
    String filePath,
    String functionName,
    String className,
    String k8sPodName,
    String k8sNodeName,
    String k8sNamespace,
    String k8sDeploymentName
) {
  public static final String DEFAULT_LANGUAGE = "LANGUAGE_UNSPECIFIED";
}
