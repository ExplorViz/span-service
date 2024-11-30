package net.explorviz.span.landscape.model.flat;

import java.util.Set;

public record FlatClass(
    String id,
    String parentId,
    String name,
    Set<String> methodIds
) {

}
