package net.explorviz.span.landscape.assembler.impl;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.explorviz.span.landscape.model.flat.FlatApplication;
import net.explorviz.span.landscape.model.flat.FlatClass;
import net.explorviz.span.landscape.model.flat.FlatMethod;
import net.explorviz.span.landscape.model.flat.FlatNode;
import net.explorviz.span.landscape.model.flat.FlatPackage;
import net.explorviz.span.landscape.model.hierarchical.Application;
import net.explorviz.span.landscape.model.flat.FlatLandscape;
import net.explorviz.span.landscape.model.hierarchical.Node;
import net.explorviz.span.landscape.model.hierarchical.Package;
import net.explorviz.span.landscape.loader.LandscapeRecord;


@ApplicationScoped
public class FlatLandscapeAssemblyCreateUtils {


  public FlatNode createNodeFromLandscapeRecord(final LandscapeRecord record) {
    final String ipAddress = record.nodeIpAddress();
    final String id = ipAddress + record.hostName();
    return new FlatNode(id, ipAddress, record.hostName(), new HashSet<>());
  }

  public FlatApplication createApplicationFromLandscapeRecord(final LandscapeRecord record, final String nodeId) {

    // Find application in node or insert new
    final String applicationName = record.applicationName();
    final int applicationInstance = record.applicationInstance();
    final String applicationLanguage = record.applicationLanguage();

    final String id = nodeId + applicationName + applicationInstance;

    return new FlatApplication(id, applicationName, applicationLanguage, applicationInstance,
        new HashSet<>());
  }

  public LinkedHashSet<FlatPackage> createPackagesFromLandscapeRecord(final LandscapeRecord record,
      final String appId) {
    // Merge package structure
    final String[] packages = record.packageName().split("\\.");

    final LinkedHashSet<FlatPackage> returnSet = new LinkedHashSet<>();

    for (int i = 0; i < packages.length - 1; i++) {
      final String current = packages[i];
      final String next = packages[i + 1];

      final String currentId = appId + current;
      final String nextId = appId + next;

      final Set<String> currentFlatPackageChildrenIds = new HashSet<>();
      currentFlatPackageChildrenIds.add(nextId);

      FlatPackage currentFlatPackage = new FlatPackage(currentId, current, currentFlatPackageChildrenIds, new HashSet<>());
      returnSet.remove(currentFlatPackage);
      returnSet.add(currentFlatPackage);

      FlatPackage nextFlatPackage = new FlatPackage(nextId, next, new HashSet<>(), new HashSet<>());
      returnSet.remove(nextFlatPackage);
      returnSet.add(nextFlatPackage);
    }

    return returnSet;
  }

  public FlatClass createClassFromRecord(final LandscapeRecord record, final String packageId) {
    final String className = record.className();
    final String id = packageId + className;
    return new FlatClass(id, className, record.className(), new HashSet<>());
  }

  public FlatMethod createMethodFromRecord(final LandscapeRecord record, final String classId) {
    return new FlatMethod(classId, record.methodName(), record.methodHash());
  }
}
