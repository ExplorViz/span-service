package net.explorviz.span.landscape.loader;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LandscapeLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(LandscapeLoader.class);

  private final AtomicLong lastRequestedLandscapes = new AtomicLong(0L);
  private final AtomicLong lastLoadedStructures = new AtomicLong(0L);

  private static final String selectSpanStructure = """
      MATCH (ss:SpanStructure)
      WHERE ss.landscape_token = $landscape_token
      RETURN ss;""";

  private static final String selectSpanStructureByTime = """
      MATCH (ss:SpanStructure)
      WHERE ss.landscape_token = $landscape_token
        AND ss.time_seen >= $from
        AND ss.time_seen <= $to
      RETURN ss;""";

  @Inject
  Driver driver;

  static Uni<Void> sessionFinalizer(ReactiveSession session) {
    return Uni.createFrom().publisher(session.close());
  }

  public Multi<LandscapeRecord> loadLandscape(final UUID landscapeToken) {
    LOGGER.atTrace().addArgument(landscapeToken).log("Loading landscape {} structure");

    return executeQuery(selectSpanStructure, Map.of("landscape_token", landscapeToken.toString()));
  }

  public Multi<LandscapeRecord> loadLandscape(final UUID landscapeToken, final long from,
      final long to) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(from).addArgument(to)
        .log("Loading landscape {} structure in time range {}-{}");

    return executeQuery(selectSpanStructureByTime,
        Map.of("landscape_token", landscapeToken.toString(), "from", from, "to", to));
  }

  private Multi<LandscapeRecord> executeQuery(final String query,
      final Map<String, Object> params) {
    lastRequestedLandscapes.incrementAndGet();

    return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeRead(tx -> {
              var result = tx.run(query, params);
              return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
            }))
        .withFinalizer(LandscapeLoader::sessionFinalizer)
        .map(LandscapeRecord::fromRecord)
        .onItem().invoke(lastLoadedStructures::incrementAndGet);
  }

  @Scheduled(every = "{explorviz.log.span.interval}")
  public void logStatus() {
    final long loadedLandscapes = this.lastRequestedLandscapes.getAndSet(0);
    final long loadedStructures = this.lastLoadedStructures.getAndSet(0);
    LOGGER.atDebug().addArgument(loadedLandscapes).addArgument(loadedStructures)
        .log("Requested {} landscapes. Loaded {} structures.");
  }
}
