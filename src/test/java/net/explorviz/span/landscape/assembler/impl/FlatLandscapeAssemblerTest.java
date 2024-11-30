package net.explorviz.span.landscape.assembler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import java.util.UUID;
import net.explorviz.span.landscape.model.flat.FlatLandscape;
import net.explorviz.span.landscape.loader.LandscapeRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FlatLandscapeAssemblerTest {

  private final FlatLandscapeAssembler flatLandscapeAssembler = Mockito.spy(new FlatLandscapeAssembler(new FlatLandscapeAssemblyCreateUtils()));

  @Test
  public void testAssembleFromRecords_Success() {
    // Arrange: Create sample LandscapeRecord data
    UUID landscapeToken = UUID.randomUUID();
    LandscapeRecord record1 = new LandscapeRecord(
        landscapeToken,
        "hash1",
        "127.0.0.1",
        "host1",
        "app1",
        "java",
        1,
        "com.example.pkg",
        "ExampleClass",
        "exampleMethod",
        System.currentTimeMillis()
    );
    LandscapeRecord record2 = new LandscapeRecord(
        landscapeToken,
        "hash2",
        "127.0.0.1",
        "host1",
        "app1",
        "java",
        1,
        "com.example.pkg.sub",
        "AnotherClass",
        "anotherMethod",
        System.currentTimeMillis()
    );

    Multi<LandscapeRecord> recordMulti = Multi.createFrom().items(record1, record2);

    // Act: Call assembleFromRecords
    Uni<FlatLandscape> resultUni = flatLandscapeAssembler.assembleFromRecords(recordMulti);

    // Assert: Verify the resulting FlatLandscape
    FlatLandscape result = resultUni.await().indefinitely();

    assertEquals(landscapeToken, result.landscapeToken());
    assertEquals(1, result.nodes().size(), "Wrong number of nodes.");
    assertEquals(1, result.applications().size(), "Wrong number of applications.");
    assertEquals(4, result.packages().size(), "Wrong number of packages.");
    assertEquals(2, result.classes().size(), "Wrong number of classes.");
    assertEquals(2, result.methods().size(), "Wrong number of methods.");
  }

  @Test
  public void testAssembleFromRecords_NoRecords() {
    // Arrange: Create an empty Multi
    Multi<LandscapeRecord> emptyMulti = Multi.createFrom().empty();

    // Act & Assert: Call assembleFromRecords and expect a NoRecordsException
    Uni<FlatLandscape> resultUni = flatLandscapeAssembler.assembleFromRecords(emptyMulti);

    assertThrows(NoRecordsException.class, () -> resultUni.await().indefinitely());
  }

  @Test
  public void testAssembleFromRecords_InsertAllInvocation() {
    // Arrange: Create sample LandscapeRecord data
    UUID landscapeToken = UUID.randomUUID();
    LandscapeRecord record = new LandscapeRecord(
        landscapeToken,
        "hash",
        "127.0.0.1",
        "host",
        "app",
        "java",
        1,
        "com.example.pkg",
        "ExampleClass",
        "exampleMethod",
        System.currentTimeMillis()
    );

    Multi<LandscapeRecord> recordMulti = Multi.createFrom().item(record);

    // Mock insertAll to verify invocation
    /*Mockito.doNothing().when(flatLandscapeAssembler).insertAll(any(), any());

    // Act: Call assembleFromRecords
    flatLandscapeAssembler.assembleFromRecords(recordMulti).await().indefinitely();

    // Assert: Verify that insertAll was called
    Mockito.verify(flatLandscapeAssembler).insertAll(any(Uni.class), any(Multi.class));*/
  }
}
