package net.explorviz.span.landscape.model.hierarchical;

import java.util.List;
import java.util.UUID;

public record Landscape(
    UUID landscapeToken,
    List<Node> nodes
) {

}
