package net.explorviz.span.landscape;

import java.util.List;
import java.util.UUID;

public record Landscape(
    UUID landscapeToken,
    List<Node> nodes,
    List<K8sNode> k8sNodes
) {

}
