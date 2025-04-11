package net.explorviz.span.landscape.assembler.impl;

import java.util.ArrayList;
import java.util.Collection;
import net.explorviz.span.landscape.Application;
import net.explorviz.span.landscape.Package;
import net.explorviz.span.landscape.assembler.LandscapeAssemblyException;

/**
 * Helper class for building/accessing package hierarchies out of strings.
 */
public final class PackageHelper {

  private PackageHelper() {
    /* Utility class */
  }

  /**
   * Matches the packages given in {@code packages} with the package hierarchy in the given
   * application {@code app}. Returns the largest index {@code i} of {@code packages} such that all
   * packages {@code packages[0], ..., packages[i-1]} are also present as a sub-tree in the
   * application. This means {@code i} is the index of the first element in the package array that
   * is not present in the hierarchy. A return value of 0 thus means that there is no root package
   * in the application matching {@code packages[0]}.
   *
   * <p>
   * E.g. if the application has the following package structure
   *
   * <pre>
   * {@code
   *  net
   *    + acme
   *      + foo
   *        - <Classes>
   *      + bar
   *        - <Classes>
   * }
   * </pre>
   *
   * </p>
   * This method returns the following for different packages:
   * <ul>
   * <li>{@code ["net", "acme"]} -> 2</li>
   * <li>{@code ["net", "foo"]} -> 1</li>
   * <li>{@code ["net", "acme", "foo", "bar"]} -> 3</li>
   * <li>{@code ["org", "acme"]} -> 0</li>
   * </ul>
   *
   * @param app      the app to search th
   * @param packages the package path match
   * @return the index {@code i} of the path such that all packages up to {@code i} are in the
   *     package hierarchy of the application
   */
  public static int lowestPackageIndex(final Application app, final String[] packages) {
    int i = 0;
    final int finalI1 = i; // Must be final for lambda expression
    Package current = app.packages().stream().filter(p -> p.name().equals(packages[finalI1]))
        .findFirst().orElse(null);
    if (current == null) {
      return 0;
    }

    i = 0;
    while (current != null) {
      i++;
      final int finalI = i; // Must be final for lambda expression
      if (finalI < packages.length) {
        current = current.subPackages().stream().filter(p -> p.name().equals(packages[finalI]))
            .findFirst().orElse(null);
      } else {
        current = null; // NOPMD
      }
    }
    return i;
  }

  /**
   * Creates a hierarchy of {@link Package}s out of a branch given as an array of package names.
   *
   * @param packages the package names to create the hierarchy out of
   * @param level the level of the root package in the application package hierarchy
   * @return the root package of the hierarchy
   */
  public static Package toHierarchy(final String[] packages, int level) {
    if (packages == null || packages.length == 0) {
      return null;
    }
    final Package rootPackage = new Package(packages[0], level, new ArrayList<>(), new ArrayList<>());
    Package currentPkg = rootPackage;
    Collection<Package> current;
    for (int i = 1; i < packages.length; i++) {
      current = currentPkg.subPackages();
      currentPkg = new Package(packages[i], level + i, new ArrayList<>(), new ArrayList<>());
      current.add(currentPkg);
    }
    return rootPackage;
  }

  /**
   * Searches through the package hierarchy for a specific path.
   *
   * @param app  the application to search in
   * @param path the path of package names to search for
   * @return The lowest {@link Package} of the path
   * @throws LandscapeAssemblyException if there is no such path in the package hierarchy
   */
  public static Package fromPath(final Application app, final String[] path) {
    if (path == null || path.length == 0) {
      throw new LandscapeAssemblyException("Path must at least contain a root");
    }
    final LandscapeAssemblyException noSuchPathException = new LandscapeAssemblyException(
        "No such path in given application");

    Package current = app.packages().stream().filter(p -> p.name().equals(path[0])).findFirst()
        .orElseThrow(() -> noSuchPathException);

    for (int i = 1; i < path.length; i++) {
      final int finalI = i;
      current = current.subPackages().stream().filter(p -> p.name().equals(path[finalI]))
          .findFirst().orElseThrow(() -> noSuchPathException);
    }
    return current;
  }
}
