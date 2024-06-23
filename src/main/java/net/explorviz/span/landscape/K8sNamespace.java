package net.explorviz.span.landscape;

import java.util.List;

public record K8sNamespace(
    String name,
    List<K8sDeployment> k8sDeployments
) {
}
