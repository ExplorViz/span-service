package net.explorviz.span.landscape;

import java.util.List;

public record K8sDeployment(
    String name,
    List<K8sPod> k8sPods
) {
}
