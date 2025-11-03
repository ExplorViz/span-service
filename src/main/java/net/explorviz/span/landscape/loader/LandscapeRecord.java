package net.explorviz.span.landscape.loader;

import java.util.Arrays;
import java.util.UUID;
import org.neo4j.driver.Record;

// TODO: Unify PersistenceSpan and LandscapeRecord? (move FQN parsing into assembler?)
public record LandscapeRecord(
    UUID landscapeToken,
    String methodHash,
    String nodeIpAddress,
    String hostName,
    String applicationName,
    String applicationLanguage,
    String applicationInstance,
    String packageName,
    String className,
    String methodName,
    String k8sPodName,
    String k8sNodeName,
    String k8sNamespace,
    String k8sDeploymentName,
    long timeSeen) {

  public static LandscapeRecord fromRecord(final Record record) {
    final UUID landscapeToken = UUID.fromString(record.get("landscape_token").asString());
    final String methodHash = record.get("method_hash").asString();
    final String nodeIpAddress = record.get("node_ip_address").asString();
    final String hostName = record.get("host_name").asString();
    final String applicationName = record.get("application_name").asString();
    final String applicationLanguage = record.get("application_language").asString();
    final String applicationInstance = record.get("application_instance").asString();
    final String methodFqn = record.get("method_fqn").asString();
    final long timeSeen = record.get("time_seen").asLong();
    final String k8sPodName = record.get("k8s_pod_name").asString();
    final String k8sNodeName = record.get("k8s_node_name").asString();
    final String k8sNamespace = record.get("k8s_namespace").asString();
    final String k8sDeploymentName = record.get("k8s_deployment_name").asString();

    // TODO: Error handling
    /*
     * By definition getFullyQualifiedOperationName().split("."): Last entry is
     * method name, next to
     * last is class name, remaining elements form the package name
     */
    final String[] operationFqnSplit = methodFqn.split("\\.");

    final String packageName = String.join(".", Arrays.copyOf(operationFqnSplit,
        operationFqnSplit.length - 2));
    final String className = operationFqnSplit[operationFqnSplit.length - 2];
    final String methodName = operationFqnSplit[operationFqnSplit.length - 1];

    return new LandscapeRecord(landscapeToken, methodHash, nodeIpAddress, hostName, applicationName,
        applicationLanguage, applicationInstance, packageName, className, methodName, k8sPodName,
        k8sNodeName, k8sNamespace, k8sDeploymentName, timeSeen);
  }
}
