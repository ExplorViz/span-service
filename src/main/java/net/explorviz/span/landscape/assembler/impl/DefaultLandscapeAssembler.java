package net.explorviz.span.landscape.assembler.impl;

import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.explorviz.span.landscape.model.hierarchical.Application;
import net.explorviz.span.landscape.model.hierarchical.Class;
import net.explorviz.span.landscape.model.hierarchical.Landscape;
import net.explorviz.span.landscape.model.hierarchical.Method;
import net.explorviz.span.landscape.model.hierarchical.Node;
import net.explorviz.span.landscape.model.hierarchical.Package;
import net.explorviz.span.landscape.assembler.LandscapeAssembler;
import net.explorviz.span.landscape.loader.LandscapeRecord;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Assemble a landscape graph out of a set of flat landscape records.
 */
@ApplicationScoped
public class DefaultLandscapeAssembler implements LandscapeAssembler<Landscape> {

  @Override
  public Landscape assembleFromRecords(final Collection<LandscapeRecord> records) {
    throw new NotImplementedException();
  }

  @Override
  public Landscape assembleFromRecords(final Multi<LandscapeRecord> records) {
    throw new NotImplementedException();
  }

  @Override
  public void insertAll(final Landscape landscape, final Collection<LandscapeRecord> records) {
    final UUID token = landscape.landscapeToken();

    // Check if all records belong to the same landscape (i.e. check token)
    if (!this.sameToken(token, records)) {
      throw new InvalidRecordException("All records must have the same token");
    }

    for (final LandscapeRecord record : records) {
      final Node node = getNodeForRecord(landscape, record);
      final Application app = getApplicationForRecord(record, node);
      final String[] packages = getPackagesForRecord(record, app);
      final Package leafPkg = PackageHelper.fromPath(app, packages);
      final Class cls = getClassForRecord(record, leafPkg);

      // Add method to class
      cls.methods().add(new Method(record.methodName(), String.valueOf(record.methodHash())));
    }
  }

  @Override
  public void insertAll(final Landscape landscape, final Multi<LandscapeRecord> records) {

  }

  private Node getNodeForRecord(final Landscape landscape, final LandscapeRecord record) {
    final Node node;

    final String ipAddress = record.nodeIpAddress();

    // Find node in landscape or insert new
    final Optional<Node> foundNode = AssemblyFindUtils.findNode(landscape, ipAddress);

    if (foundNode.isPresent()) {
      node = foundNode.get();
    } else {
      node = new Node(ipAddress, record.hostName(), new ArrayList<>());
      landscape.nodes().add(node);
    }

    return node;
  }

  private Application getApplicationForRecord(final LandscapeRecord record, final Node node) {
    final Application app;

    // Find application in node or insert new
    final String applicationName = record.applicationName();
    final int applicationInstance = record.applicationInstance();
    final String applicationLanguage = record.applicationLanguage();
    final Optional<Application> foundApp =
        AssemblyFindUtils.findApplication(node, applicationName, applicationInstance);
    if (foundApp.isPresent()) {
      app = foundApp.get();
    } else {
      app = new Application(applicationName, applicationLanguage, applicationInstance,
          new ArrayList<>());
      node.applications().add(app);
    }

    return app;
  }

  private String[] getPackagesForRecord(final LandscapeRecord record, final Application app) {
    // Merge package structure
    final String[] packages = record.packageName().split("\\.");
    final int unknownPkgIndex = PackageHelper.lowestPackageIndex(app, packages);

    if (unknownPkgIndex < packages.length) {
      final String[] pksToInsert = Arrays.copyOfRange(packages, unknownPkgIndex, packages.length);
      final Package rootToInsert = PackageHelper.toHierarchy(pksToInsert);
      // Merge missing packages
      if (unknownPkgIndex == 0) {
        // Add new root package
        app.packages().add(rootToInsert);
      } else {
        // Merge into hierarchy
        final String[] existing = Arrays.copyOfRange(packages, 0, unknownPkgIndex);
        final Package lowest = PackageHelper.fromPath(app, existing);
        lowest.subPackages().add(rootToInsert);
      }
    }

    return packages;
  }

  private Class getClassForRecord(final LandscapeRecord record, final Package leafPkg) {
    // Get or creat class
    final Class cls;
    final Optional<Class> foundCls = AssemblyFindUtils.findClazz(leafPkg, record.className());
    if (foundCls.isPresent()) {
      cls = foundCls.get();
    } else {
      cls = new Class(record.className(), new ArrayList<>());
      leafPkg.classes().add(cls);
    }

    return cls;
  }

  private boolean sameToken(final UUID token, final Collection<LandscapeRecord> records) {
    return records.stream().allMatch(r -> token.equals(r.landscapeToken()));
  }
}
