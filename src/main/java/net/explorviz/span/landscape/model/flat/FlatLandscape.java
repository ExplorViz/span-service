package net.explorviz.span.landscape.model.flat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.explorviz.span.landscape.model.hierarchical.Application;
import net.explorviz.span.landscape.model.hierarchical.Class;
import net.explorviz.span.landscape.model.hierarchical.Method;
import net.explorviz.span.landscape.model.hierarchical.Node;
import net.explorviz.span.landscape.model.hierarchical.Package;

public record FlatLandscape(
    UUID landscapeToken,
    Map<String, FlatNode> nodes,
    Map<String,FlatApplication> applications,
    Map<String,FlatPackage> packages,
    Map<String,FlatClass> classes,
    Map<String,FlatMethod> methods
) {

}
