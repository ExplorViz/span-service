package net.explorviz.span.landscape.assembler.impl;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;
import net.explorviz.span.landscape.FlatLandscape;
import net.explorviz.span.landscape.assembler.LandscapeAssembler;
import net.explorviz.span.landscape.loader.LandscapeRecord;
import org.apache.commons.lang3.NotImplementedException;

public class FlatLandscapeAssembler implements LandscapeAssembler<Uni<FlatLandscape>> {
  @Override
  public Uni<FlatLandscape> assembleFromRecords(final Collection<LandscapeRecord> records) {
    return null;
  }

  @Override
  public Uni<FlatLandscape> assembleFromRecords(final Multi<LandscapeRecord> records) {
    return records
        .toUni() // Convert Multi to Uni for the first record
        .onItem()
        .transform(LandscapeRecord::landscapeToken) // Extract token
        .onFailure(NoSuchElementException.class)
        .recoverWithUni(Uni.createFrom().failure(new NoRecordsException())) // Handle no records
        .onItem()
        .transform(token -> new FlatLandscape(token, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())) // Create FlatLandscape
        .invoke(landscape -> this.insertAll(Uni.createFrom().item(landscape), records)) // Call insertAll as a side effect
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

  }
}
