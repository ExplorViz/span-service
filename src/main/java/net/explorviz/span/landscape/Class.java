package net.explorviz.span.landscape;

import java.util.List;

public record Class(
    String name,
    int level,
    List<Method> methods
) {

}
