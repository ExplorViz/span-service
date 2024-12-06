package net.explorviz.span.api;

import java.util.Optional;
import java.util.UUID;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.timestamp.TimestampLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TimestampResourceTest {

  TimestampResource timestampResource;
  TimestampLoader mockedTimestampLoader;

  @BeforeEach
  public void setUp() {
    mockedTimestampLoader = Mockito.mock(TimestampLoader.class);
    this.timestampResource = new TimestampResource(mockedTimestampLoader);
  }

  @Test
  public void testGetAllTimestampsForToken() {
    final String expectedToken = PersistenceSpan.DEFAULT_UUID.toString();

    this.timestampResource.getTimestamps(expectedToken, 0L, 0L, Optional.empty());

    Mockito.verify(mockedTimestampLoader, Mockito.times(1))
        .loadAllTimestampsForToken(UUID.fromString(expectedToken), Optional.empty());
  }

  @Test
  public void testGetAllTimestampsForTokenAndCommit() {
    final String expectedToken = PersistenceSpan.DEFAULT_UUID.toString();

    this.timestampResource.getTimestamps(expectedToken, 0L, 0L, Optional.of("commit"));

    Mockito.verify(mockedTimestampLoader, Mockito.times(1))
        .loadAllTimestampsForToken(UUID.fromString(expectedToken), Optional.of("commit"));
  }

  @Test
  public void testGetNewerTimestampsForTokenAndCommit() {
    final String expectedToken = PersistenceSpan.DEFAULT_UUID.toString();

    this.timestampResource.getTimestamps(expectedToken, 1715367170000L, 0L, Optional.of("commit"));

    Mockito.verify(mockedTimestampLoader, Mockito.times(1))
        .loadNewerTimestampsForToken(UUID.fromString(expectedToken), 1715367170000L,
            Optional.of("commit"));
  }

  @Test
  public void testGetNewerTimestampsForToken() {
    final String expectedToken = PersistenceSpan.DEFAULT_UUID.toString();

    this.timestampResource.getTimestamps(expectedToken, 1715367170000L, 0L, Optional.empty());

    Mockito.verify(mockedTimestampLoader, Mockito.times(1))
        .loadNewerTimestampsForToken(UUID.fromString(expectedToken), 1715367170000L,
            Optional.empty());
  }



}
