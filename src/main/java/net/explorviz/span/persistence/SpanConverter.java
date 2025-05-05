package net.explorviz.span.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import net.explorviz.avro.Span;
import net.explorviz.span.hash.HashHelper;
import org.apache.kafka.streams.kstream.ValueMapper;

@ApplicationScoped
public class SpanConverter implements ValueMapper<Span, PersistenceSpan> {

  @Override
  public PersistenceSpan apply(final Span span) {
    final String landscapeTokenRaw = span.getLandscapeToken();
    final String gitCommitChecksum = span.getGitCommitChecksum();
    // TODO: Remove invalid UUID hotfix
    UUID landscapeToken = PersistenceSpan.DEFAULT_UUID;
    if (!"mytokenvalue".equals(landscapeTokenRaw)) {
      landscapeToken = UUID.fromString(landscapeTokenRaw);
    }

    final long startTime = span.getStartTimeEpochMilli();
    final long endTime = span.getEndTimeEpochMilli();
    final String nodeIpAddress = span.getHostIpAddress();
    final String nodeHostName = span.getHostname();
    final String applicationName = span.getAppName();
    final String applicationInstance = span.getAppInstanceId();
    final String applicationLanguage = span.getAppLanguage();
    final String methodFqn = span.getFullyQualifiedOperationName();
    final String k8sPodName = span.getK8sPodName();
    final String k8sNodeName = span.getK8sNodeName();
    final String k8sNamespace = span.getK8sNamespace();
    final String k8sDeploymentName = span.getK8sDeploymentName();

    final String methodHashCode = HashHelper.calculateSpanHash(landscapeToken, nodeIpAddress,
        applicationName, applicationInstance, methodFqn, k8sPodName, k8sNodeName, k8sNamespace,
        k8sDeploymentName);

    return new PersistenceSpan(landscapeToken, gitCommitChecksum, span.getSpanId(),
        span.getParentSpanId(),
        span.getTraceId(), startTime, endTime,
        nodeIpAddress, nodeHostName, applicationName, applicationLanguage, applicationInstance,
        methodFqn, methodHashCode, k8sPodName, k8sNodeName, k8sNamespace, k8sDeploymentName);
  }
}
