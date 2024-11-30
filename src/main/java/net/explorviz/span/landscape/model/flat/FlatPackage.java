package net.explorviz.span.landscape.model.flat;

import java.util.Set;

public record FlatPackage(
    String id,
    String name,
    Set<String> subPackageIds,
    Set<String> classIds
) {

}
