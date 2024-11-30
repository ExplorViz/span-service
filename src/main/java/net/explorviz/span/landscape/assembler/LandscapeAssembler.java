package net.explorviz.span.landscape.assembler;

import io.smallrye.mutiny.Multi;
import java.util.Collection;
import java.util.Collections;
import net.explorviz.span.landscape.loader.LandscapeRecord;

// TODO: Change so it can start assembling from Multi instead of Collection?
public interface LandscapeAssembler<T> {

  /**
   * Assembles a landscape model out of a collection of {@link LandscapeRecord}s. The resulting
   * landscape is a hierarchical/tree representation of all records. All records must have the same
   * token ({@link LandscapeRecord#landscapeToken()}).
   *
   * @param records the records to build the model out of
   * @return the assembled landscape model
   * @throws LandscapeAssemblyException if the landscape could not be assembled
   */
  T assembleFromRecords(Collection<LandscapeRecord> records);

  T assembleFromRecords(Multi<LandscapeRecord> records);


  /**
   * Inserts a new record into an existing landscape model. If the record is already included this
   * is a no-op. The new record must have the same landscape token as the landscape.
   *
   * @param landscape the landscape
   * @param newRecord the record to insert
   * @throws LandscapeAssemblyException if the record could not be included
   */
  default void insert(final T landscape, final LandscapeRecord newRecord) {
    this.insertAll(landscape, Collections.singleton(newRecord));
  }

  /**
   * Inserts all records into an existing landscape model. Record already included in the landscape
   * are ignored. Every new record must have the same landscape token as the landscape.
   *
   * @param landscape the landscape to insert the records into
   * @param records   the records to insert
   * @throws LandscapeAssemblyException if at least one record could not be inserted.
   */
  void insertAll(T landscape, Collection<LandscapeRecord> records);

  void insertAll(T landscape, Multi<LandscapeRecord> records);
}
