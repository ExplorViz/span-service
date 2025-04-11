package net.explorviz.span.landscape;

import java.util.List;

public record Package(
    String name,
    int level,
    List<Package> subPackages,
    List<Class> classes
) {

}
