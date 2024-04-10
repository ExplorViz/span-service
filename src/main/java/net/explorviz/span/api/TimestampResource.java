package net.explorviz.span.api;

import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.UUID;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.timestamp.Timestamp;
import net.explorviz.span.timestamp.TimestampLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v2/landscapes")
@Produces(MediaType.APPLICATION_JSON)
public class TimestampResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(TimestampResource.class);

  @Inject
  TimestampLoader timestampLoader;

  @GET
  @Path("/{token}/timestamps")
  public Multi<Timestamp> getTimestamps(@PathParam("token") final String token,
      @QueryParam("newest") final long newest, @QueryParam("oldest") final long oldest,
      @QueryParam("commit") final Optional<String> commit) {
    LOGGER.atInfo().addArgument(token).addArgument(commit.orElse("all-commits"))
        .log("Loading all timestamps for token {} and commit {}");

    if (newest == 0 && oldest == 0) {
      return this.timestampLoader.loadAllTimestampsForToken(parseUuid(token), commit);
    }

    if (newest != 0) {
      return this.timestampLoader.loadNewerTimestampsForToken(parseUuid(token), newest, commit);
    }
    return Multi.createFrom().empty();
  }

  private UUID parseUuid(final String token) {
    // TODO: Remove invalid token hotfix
    if ("mytokenvalue".equals(token)) {
      return PersistenceSpan.DEFAULT_UUID;
    }

    try {
      return UUID.fromString(token);
    } catch (final IllegalArgumentException e) {
      throw new BadRequestException("Invalid token", e);
    }
  }

}
