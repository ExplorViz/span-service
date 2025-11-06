package net.explorviz.span.timestamp;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TimestampLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimestampLoader.class);

  private static final String selectAllTimestampsForToken = """
      MATCH (sc:SpanCount)
      WHERE sc.landscape_token = $landscape_token
      RETURN sc;""";

  private static final String selectAllTimestampsForTokenAndCommit = """
      MATCH (sc:SpanCountCommit)
      WHERE sc.landscape_token = $landscape_token
        AND sc.git_commit_checksum = $git_commit_checksum
      RETURN sc;""";

  private static final String selectNewerTimestampsForToken = """
      MATCH (sc:SpanCount)
      WHERE sc.landscape_token = $landscape_token
        AND sc.tenth_second_epoch > $newest
      RETURN sc;""";

  private static final String selectNewerTimestampsForTokenAndCommit = """
      MATCH (sc:SpanCountCommit)
      WHERE sc.landscape_token = $landscape_token
        AND sc.git_commit_checksum = $git_commit_checksum
        AND sc.tenth_second_epoch > $newest
      RETURN sc;""";

  @Inject
  Driver driver;

  static Uni<Void> sessionFinalizer(ReactiveSession session) {
    return Uni.createFrom().publisher(session.close());
  }

  public Multi<Timestamp> loadAllTimestampsForToken(final UUID landscapeToken,
      final Optional<String> commit) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(commit.orElse("all-commits"))
        .log("Loading all timestamps for token {} and commit {}");

    String query =
        commit.isPresent() ? selectAllTimestampsForTokenAndCommit : selectAllTimestampsForToken;
    Map<String, Object> params = Map.of(
        "landscape_token", landscapeToken.toString(),
        "git_commit_checksum", commit.orElse(""));

    return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeRead(tx -> {
              var result = tx.run(query, params);
              return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
            }))
        .withFinalizer(TimestampLoader::sessionFinalizer)
        .map(record -> Timestamp.fromNode(record.get("sc").asNode()));
  }

  public Multi<Timestamp> loadNewerTimestampsForToken(UUID landscapeToken, long newest,
      Optional<String> commit) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(newest)
        .addArgument(commit.orElse("all-commits"))
        .log("Loading newer timestamps for token {} and newest timestamp {} and commit {}.");

    String query =
        commit.isPresent() ? selectNewerTimestampsForTokenAndCommit : selectNewerTimestampsForToken;
    Map<String, Object> params = Map.of(
        "landscape_token", landscapeToken.toString(),
        "git_commit_checksum", commit.orElse(null),
        "newest", newest);

    return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
            session -> session.executeRead(tx -> {
              var result = tx.run(query, params);
              return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
            }))
        .withFinalizer(TimestampLoader::sessionFinalizer)
        .map(record -> Timestamp.fromNode(record.get("sc").asNode()));
  }
}
