package net.explorviz.span.timestamp;

import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TimestampLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimestampLoader.class);

  private final QuarkusCqlSession session;

  private final PreparedStatement selectAllTimestampsForToken;
  private final PreparedStatement selectAllTimestampsForTokenAndCommit;
  private final PreparedStatement selectNewerTimestampsForToken;
  private final PreparedStatement selectNewerTimestampsForTokenAndCommit;

  @Inject
  public TimestampLoader(final QuarkusCqlSession session) {
    this.session = session;

    this.selectAllTimestampsForToken = session.prepare(
        "SELECT * " + "FROM span_count_per_time_bucket_and_token "
            + "WHERE landscape_token = ?");

    this.selectNewerTimestampsForToken = session.prepare(
        "SELECT * " + "FROM span_count_per_time_bucket_and_token "
            + "WHERE landscape_token = ? AND tenth_second_epoch > ?");

    this.selectAllTimestampsForTokenAndCommit = session.prepare(
        "SELECT * " + "FROM span_count_for_token_and_commit_and_time_bucket "
            + "WHERE landscape_token = ? AND git_commit_checksum = ?");

    this.selectNewerTimestampsForTokenAndCommit = session.prepare(
        "SELECT * " + "FROM span_count_for_token_and_commit_and_time_bucket "
            + "WHERE landscape_token = ? AND git_commit_checksum = ? AND "
            + "tenth_second_epoch > ?");
  }

  public Multi<Timestamp> loadAllTimestampsForToken(final UUID landscapeToken,
      final Optional<String> commit) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(commit.orElse("all-commits"))
        .log("Loading all timestamps for token {} and commit {}");

    if (commit.isPresent()) {
      return session.executeReactive(
              this.selectAllTimestampsForTokenAndCommit.bind(landscapeToken, commit.get()))
          .map(Timestamp::fromRow);
    } else {
      return session.executeReactive(this.selectAllTimestampsForToken.bind(landscapeToken))
          .map(Timestamp::fromRow);
    }
  }

  public Multi<Timestamp> loadNewerTimestampsForToken(UUID landscapeToken, long newest,
      Optional<String> commit) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(newest)
        .addArgument(commit.orElse("all-commits"))
        .log("Loading newer timestamps for token {} and newest timestamp {} and commit {}.");

    if (commit.isPresent()) {
      return session.executeReactive(
              this.selectNewerTimestampsForTokenAndCommit.bind(landscapeToken, commit.get(),
                  newest))
          .map(Timestamp::fromRow);
    } else {
      return session.executeReactive(
              this.selectNewerTimestampsForToken.bind(landscapeToken, newest))
          .map(Timestamp::fromRow);
    }


  }
}
