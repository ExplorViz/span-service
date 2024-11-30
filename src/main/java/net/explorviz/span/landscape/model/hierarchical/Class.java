package net.explorviz.span.landscape.model.hierarchical;

import java.util.List;

public record Class(
    String name,
    List<Method> methods
) {

}
