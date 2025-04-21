package net.explorviz.span.landscape.loader;

import com.datastax.oss.driver.api.core.cql.Row;
import java.util.Arrays;
import java.util.UUID;

// TODO: Unify PersistenceSpan and LandscapeRecord? (move FQN parsing into assembler?)
public record LandscapeRecord(
    UUID landscapeToken,
    String methodHash,
    String nodeIpAddress,
    String hostName,
    String applicationName,
    String applicationLanguage,
    int applicationInstance,
    String packageName,
    String className,
    String methodName,
    String k8sPodName,
    String k8sNodeName,
    String k8sNamespace,
    String k8sDeploymentName,
    long timeSeen) {

  public static LandscapeRecord fromRow(final Row row) {
    final UUID landscapeToken = row.getUuid("landscape_token");
    final String methodHash = row.getString("method_hash");
    final String nodeIpAddress = row.getString("node_ip_address");
    final String hostName = row.getString("host_name");
    final String applicationName = row.getString("application_name");
    final String applicationLanguage = row.getString("application_language");
    final int applicationInstance = Integer.parseInt(row.getString("application_instance")); // cassandra init from deployment is deleted, therefore the toString conversion is not needed anymore
    final String methodFqn = row.getString("method_fqn");
    final long timeSeen = row.getLong("time_seen");
    final String k8sPodName = row.getString("k8s_pod_name");
    final String k8sNodeName = row.getString("k8s_node_name");
    final String k8sNamespace = row.getString("k8s_namespace");
    final String k8sDeploymentName = row.getString("k8s_deployment_name");

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
