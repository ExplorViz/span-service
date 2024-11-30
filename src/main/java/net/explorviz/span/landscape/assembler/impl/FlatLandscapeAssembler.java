package net.explorviz.span.landscape.assembler.impl;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.unchecked.Unchecked;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Application;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import net.explorviz.span.landscape.assembler.LandscapeAssembler;
import net.explorviz.span.landscape.loader.LandscapeRecord;
import net.explorviz.span.landscape.model.flat.FlatApplication;
import net.explorviz.span.landscape.model.flat.FlatClass;
import net.explorviz.span.landscape.model.flat.FlatLandscape;
import net.explorviz.span.landscape.model.flat.FlatMethod;
import net.explorviz.span.landscape.model.flat.FlatNode;
import net.explorviz.span.landscape.model.flat.FlatPackage;
import org.apache.commons.lang3.NotImplementedException;

@ApplicationScoped
public class FlatLandscapeAssembler implements LandscapeAssembler<Uni<FlatLandscape>> {

  private final FlatLandscapeAssemblyCreateUtils assembler;

  @Inject
  public FlatLandscapeAssembler(FlatLandscapeAssemblyCreateUtils assembler) {
    this.assembler = assembler;
  }

  @Override
  public Uni<FlatLandscape> assembleFromRecords(final Collection<LandscapeRecord> records) {
    return null;
  }

  @Override
  public Uni<FlatLandscape> assembleFromRecords(final Multi<LandscapeRecord> records) {
    return records
        .toUni() // Convert Multi to Uni for the first record
        .onFailure(NoSuchElementException.class) // Catch the NoSuchElementException
        .recoverWithUni(() -> Uni.createFrom().failure(new NoRecordsException())) // Convert it to NoRecordsException
        .onItem()
        .ifNull()
        .failWith(new NoRecordsException("No records found")) // Extra safeguard for nulls
        .onItem()
        .transform(LandscapeRecord::landscapeToken)
        .onItem()
        .transform(token -> new FlatLandscape(token, new HashMap<>(), new HashMap<>(),
            new HashMap<>(), new HashMap<>(), new HashMap<>())) // Create FlatLandscape
        .invoke(landscape -> this.insertAll(Uni.createFrom().item(landscape), records))
        .onItem()
        .transform(landscape -> landscape);
  }

  @Override
  public void insertAll(final Uni<FlatLandscape> landscape,
      final Collection<LandscapeRecord> records) {
    throw new NotImplementedException();
  }

  @Override
  public void insertAll(final Uni<FlatLandscape> landscape, final Multi<LandscapeRecord> records) {
    landscape
        .subscribe()
        .with(
            flatLandscape -> {
              // Process each LandscapeRecord reactively
              records
                  .subscribe()
                  .with(Unchecked.consumer(record -> {
                    final UUID token = flatLandscape.landscapeToken();

                    // Validate the token for each record
                    if (!token.equals(record.landscapeToken())) {
                      throw new InvalidRecordException(
                          "Record token does not match landscape token");
                    }

                    // Process the record
                    final FlatNode node =
                        this.assembler.createNodeFromLandscapeRecord(record);

                    final FlatApplication app =
                        this.assembler.createApplicationFromLandscapeRecord(record, node.id());

                    // use this type to highlight insertion-order, i.e., last element is package
                    // which contains the class of this landscape record
                    final LinkedHashSet<FlatPackage> packages =
                        this.assembler.createPackagesFromLandscapeRecord(record, app.id());

                    final FlatPackage packageWithClass = packages.getLast();

                    final FlatClass cls =
                        this.assembler.createClassFromRecord(record, packageWithClass.id());
                    packageWithClass.classIds().add(cls.id());

                    final FlatMethod method =
                        this.assembler.createMethodFromRecord(record, cls.id());
                    cls.methodIds().add(method.methodHash());

                    // update all
                    flatLandscape.nodes().computeIfAbsent(node.id(), id -> node)
                        .applicationIds().add(app.id());

                    flatLandscape.applications().computeIfAbsent(app.id(), id -> app)
                        .packagIds().addAll(app.packagIds());

                    packages.stream().forEach((pckg) -> {
                      flatLandscape.packages().computeIfAbsent(pckg.id(), id -> pckg)
                          .subPackageIds().addAll(pckg.subPackageIds());

                      flatLandscape.packages().computeIfAbsent(pckg.id(), id -> pckg)
                          .classIds().addAll(pckg.classIds());
                    });

                    flatLandscape.classes().computeIfAbsent(cls.id(), id -> cls)
                        .methodIds().addAll(cls.methodIds());

                    flatLandscape.methods().computeIfAbsent(String.valueOf(method.hashCode()), id -> method);

                  }), failure -> {
                    // Handle errors in processing LandscapeRecords
                    System.err.println("Error processing LandscapeRecord: " + failure.getMessage());
                  });
            },
            failure -> {
              // Handle errors in resolving the FlatLandscape
              System.err.println("Failed to resolve FlatLandscape: " + failure.getMessage());
            }
        );
  }

}
