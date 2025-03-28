package net.explorviz.span.landscape.assembler.impl;

import java.util.Optional;
import net.explorviz.span.landscape.Application;
import net.explorviz.span.landscape.Class;
import net.explorviz.span.landscape.Landscape;
import net.explorviz.span.landscape.Node;
import net.explorviz.span.landscape.Package;

/**
 * Utility class that provides various methods for finding elements in a landscape graph.
 */
public final class AssemblyUtils {

  private AssemblyUtils() {
    /* Utility Class */
  }

  /**
   * Searches for a {@link Node} in a landscape.
   *
   * @param landscape the landscape
   * @param ipAddress the ip address of the node to find
   * @return an optional that contains the node if it is included in the landscape, and is empty
   *     otherwise
   */
  public static Optional<Node> findNode(final Landscape landscape, final String ipAddress) {
    for (final Node n : landscape.nodes()) {
      if (n.ipAddress().equals(ipAddress)) {
        return Optional.of(n);
      }
    }

    return Optional.empty();
  }

  /**
   * Searches for an {@link Application} in a node.
   *
   * @param node     the node
   * @param instance the instanceId id of the application to search for
   * @return an optional that contains the app if it is included in the node, and is empty otherwise
   */
  public static Optional<Application> findApplication(final Node node, final String name,
      final String instance) {
    for (final Application a : node.applications()) {
      if (a.instanceId().equals(instance) && a.name().equals(name)) {
        return Optional.of(a);
      }
    }

    return Optional.empty();
  }

  /**
   * Searches fo a {@link Class} in a package.
   *
   * @param pkg       the package to search in
   * @param className the name of the class to search for
   * @return an optional that contains the class if it is included in the package, and is empty
   *     otherwise
   */
  public static Optional<Class> findClazz(final Package pkg, final String className) {
    return pkg.classes().stream().filter(c -> c.name().equals(className)).findAny();
  }
}
