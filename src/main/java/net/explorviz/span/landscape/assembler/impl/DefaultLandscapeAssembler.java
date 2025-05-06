package net.explorviz.span.landscape.assembler.impl;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.explorviz.span.landscape.Application;
import net.explorviz.span.landscape.Class;
import net.explorviz.span.landscape.K8sDeployment;
import net.explorviz.span.landscape.K8sNamespace;
import net.explorviz.span.landscape.K8sNode;
import net.explorviz.span.landscape.K8sPod;
import net.explorviz.span.landscape.Landscape;
import net.explorviz.span.landscape.Method;
import net.explorviz.span.landscape.Node;
import net.explorviz.span.landscape.Package;
import net.explorviz.span.landscape.assembler.LandscapeAssembler;
import net.explorviz.span.landscape.loader.LandscapeRecord;

/**
 * Assemble a landscape graph out of a set of flat landscape records.
 */
@ApplicationScoped
public class DefaultLandscapeAssembler implements LandscapeAssembler {

  @Override
  public Landscape assembleFromRecords(final Collection<LandscapeRecord> records) {
    final UUID token =
        records.stream().findFirst().orElseThrow(NoRecordsException::new).landscapeToken();

    // Create empty landscape and insert all records
    final Landscape landscape = new Landscape(token, new ArrayList<>(), new ArrayList<>());

    this.insertAll(landscape, records);
    return landscape;
  }

  @Override
  public void insertAll(final Landscape landscape, final Collection<LandscapeRecord> records) {
    final UUID token = landscape.landscapeToken();

    // Check if all records belong to the same landscape (i.e. check token)
    if (!this.sameToken(token, records)) {
      throw new InvalidRecordException("All records must have the same token");
    }

    for (final LandscapeRecord record : records) {
      final Optional<K8sConstructs> k8sConstructs = getK8sConstructsForRecord(record, landscape);
      final Node node = k8sConstructs.isEmpty() ? getNodeForRecord(landscape, record) : null;
      final Application app =
          k8sConstructs.isEmpty() ? getApplicationForRecord(record, node) : k8sConstructs.get()
              .app();
      final String[] packages = getPackagesForRecord(record, app);
      final Package leafPkg = PackageHelper.fromPath(app, packages);
      final Class cls = getClassForRecord(record, leafPkg);

      // Add method to class
      cls.methods().add(new Method(record.methodName(), String.valueOf(record.methodHash())));
    }
  }

  private Node getNodeForRecord(final Landscape landscape, final LandscapeRecord record) {
    final Node node;

    final String ipAddress = record.nodeIpAddress();

    // Find node in landscape or insert new
    final Optional<Node> foundNode = AssemblyUtils.findNode(landscape, ipAddress);

    if (foundNode.isPresent()) {
      node = foundNode.get();
    } else {
      node = new Node(ipAddress, record.hostName(), new ArrayList<>());
      landscape.nodes().add(node);
    }

    return node;
  }

  private record K8sConstructs(
      K8sPod pod,
      K8sNode node,
      K8sNamespace namespace,
      K8sDeployment deployment,
      Application app
  ) {
  }

  private Optional<K8sConstructs> getK8sConstructsForRecord(final LandscapeRecord record,
      final Landscape landscape) {
    final var podName = record.k8sPodName();
    final var nodeName = record.k8sNodeName();
    final var namespaceName = record.k8sNamespace();
    final var deploymentName = record.k8sDeploymentName();

    if (record.k8sPodName() == null || record.k8sPodName().isEmpty()) {
      return Optional.empty();
    }

    var node = landscape.k8sNodes().stream().filter(n -> Objects.equals(n.name(), nodeName))
        .findFirst().orElse(null);
    if (node == null) {
      node = new K8sNode(nodeName, new ArrayList<>());
      landscape.k8sNodes().add(node);
    }

    var namespace =
        node.k8sNamespaces().stream().filter(n -> Objects.equals(n.name(), namespaceName))
            .findFirst().orElse(null);
    if (namespace == null) {
      namespace = new K8sNamespace(namespaceName, new ArrayList<>());
      node.k8sNamespaces().add(namespace);
    }

    var deployment = namespace.k8sDeployments().stream()
        .filter(d -> Objects.equals(d.name(), deploymentName)).findFirst().orElse(null);
    if (deployment == null) {
      deployment = new K8sDeployment(deploymentName, new ArrayList<>());
      namespace.k8sDeployments().add(deployment);
    }

    var pod = deployment.k8sPods().stream().filter(p -> Objects.equals(p.name(), podName))
        .findFirst().orElse(null);
    if (pod == null) {
      pod = new K8sPod(podName, new ArrayList<>());
      deployment.k8sPods().add(pod);
    }


    final String applicationName = record.applicationName();
    final String applicationInstance = record.applicationInstance();
    final String applicationLanguage = record.applicationLanguage();
    var app = pod.applications().stream()
        .filter(
            a -> Objects.equals(a.name(), applicationName) && a.instanceId() == applicationInstance)
        .findFirst().orElse(null);
    if (app == null) {
      app = new Application(applicationName, applicationLanguage, applicationInstance,
          new ArrayList<>());
      pod.applications().add(app);
    }

    return Optional.of(new K8sConstructs(pod, node, namespace, deployment, app));
  }

  private Application getApplicationForRecord(final LandscapeRecord record, final Node node) {
    final Application app;

    // Find application in node or insert new
    final String applicationName = record.applicationName();
    final String applicationInstance = record.applicationInstance();
    final String applicationLanguage = record.applicationLanguage();
    final Optional<Application> foundApp =
        AssemblyUtils.findApplication(node, applicationName, applicationInstance);
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
    // This array of packages represents one path in the component tree
    final String[] packages = record.packageName().split("\\.");
    final int unknownPkgIndex = PackageHelper.lowestPackageIndex(app, packages);

    if (unknownPkgIndex < packages.length) {
      final String[] pksToInsert = Arrays.copyOfRange(packages, unknownPkgIndex, packages.length);
      final Package rootToInsert = PackageHelper.toHierarchy(pksToInsert, unknownPkgIndex);
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
    final Optional<Class> foundCls = AssemblyUtils.findClass(leafPkg, record.className());
    if (foundCls.isPresent()) {
      cls = foundCls.get();
    } else {
      int classLevel = leafPkg.level() + 1;
      cls = new Class(record.className(), classLevel, new ArrayList<>());
      leafPkg.classes().add(cls);
    }

    return cls;
  }

  private boolean sameToken(final UUID token, final Collection<LandscapeRecord> records) {
    return records.stream().allMatch(r -> token.equals(r.landscapeToken()));
  }
}
