package net.explorviz.span.landscape.assembler.impl;

import net.explorviz.span.landscape.model.hierarchical.Application;
import net.explorviz.span.landscape.model.hierarchical.Class;
import net.explorviz.span.landscape.model.hierarchical.Node;
import net.explorviz.span.landscape.model.hierarchical.Package;
import net.explorviz.span.landscape.loader.LandscapeRecord;

public interface LandscapeAssemblyCreateUtils<T> {

  Node addNodeFromRecordToLandscape(final T landscape, final LandscapeRecord record);
  Application addApplicationFromRecordToNode(final LandscapeRecord record, final Node node);
  String[] addPackagesFromRecordToApplication(final LandscapeRecord record, final Application app);
  Class addClassFromRecordToPackage(final LandscapeRecord record, final Package leafPkg);

}
