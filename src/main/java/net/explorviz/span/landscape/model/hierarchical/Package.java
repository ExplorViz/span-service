package net.explorviz.span.landscape.model.hierarchical;

import java.util.List;

public record Package(
    String name,
    List<Package> subPackages,
    List<Class> classes
) {

}
