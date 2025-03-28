package net.explorviz.span.landscape;

import java.util.List;

public record Application(
    String name,
    String language,
    String instanceId,
    List<Package> packages
) {

}
