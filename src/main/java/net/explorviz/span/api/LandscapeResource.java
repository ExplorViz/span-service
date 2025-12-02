package net.explorviz.span.api;

import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.explorviz.span.landscape.Landscape;
import net.explorviz.span.landscape.assembler.LandscapeAssembler;
import net.explorviz.span.landscape.assembler.LandscapeAssemblyException;
import net.explorviz.span.landscape.assembler.impl.NoRecordsException;
import net.explorviz.span.landscape.loader.LandscapeLoader;
import net.explorviz.span.landscape.loader.LandscapeRecord;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.timestamp.Timestamp;
import net.explorviz.span.timestamp.TimestampLoader;
import net.explorviz.span.trace.Span;
import net.explorviz.span.persistence.PersistenceSpanProcessor;
import net.explorviz.span.trace.Trace;
import net.explorviz.span.trace.TraceLoader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v2/landscapes")
@Produces(MediaType.APPLICATION_JSON)
public class LandscapeResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(LandscapeResource.class);

  @Inject
  public LandscapeLoader landscapeLoader;

  @Inject
  public TimestampLoader timestampLoader;

  @Inject
  public LandscapeAssembler landscapeAssembler;

  @Inject
  public TraceLoader traceLoader;

  @Inject
  public QuarkusCqlSession session;

  @Inject
  public PersistenceSpanProcessor spanProcessor;

  @ConfigProperty(name = "explorviz.span.api.timeverification.enabled")
  /* default */ boolean isTimeVerificationEnabled;

  @GET
  @Path("/{token}/structure")
  @Operation(summary = "Retrieve a landscape graph",
      description = "Assembles the (possibly empty) landscape of "
          + "all spans observed in the given time range")
  @APIResponses(@APIResponse(responseCode = "200", description = "Success",
      content = @Content(mediaType = "application/json",
          schema = @Schema(implementation = Landscape.class))))
  public Uni<Landscape> getStructure(@PathParam("token") final String token,
      @QueryParam("from") final Long from, @QueryParam("to") final Long to) {
    final Multi<LandscapeRecord> recordMulti;

    if (!isTimeVerificationEnabled || from == null || to == null) {
      // TODO: Cache (shared with PersistenceSpanProcessor?)
      recordMulti = landscapeLoader.loadLandscape(parseUuid(token));
    } else {
      // TODO: Remove millisecond/nanosecond mismatch hotfix
      recordMulti = landscapeLoader.loadLandscape(parseUuid(token), from, to);
    }

    return recordMulti.collect().asList().map(landscapeAssembler::assembleFromRecords)
        .onFailure(NoRecordsException.class)
        .transform(t -> new NotFoundException("Landscape not found or empty", t))
        .onFailure(LandscapeAssemblyException.class).transform(
            t -> new InternalServerErrorException("Landscape assembly error: " + t.getMessage(),
                t));
  }

  @GET
  @Path("/{token}/dynamic")
  public Multi<Trace> getDynamic(@PathParam("token") final String token,
      @QueryParam("from") final Long from, @QueryParam("exact") final Long exact,  @QueryParam("to") final Long to, 
      @QueryParam("commit") final Optional<String> commit) {

    if (!isTimeVerificationEnabled || (from == null && to == null)) {
      if (!isTimeVerificationEnabled) {
        LOGGER.atWarn().log("Time ranges are disabled, will always return ALL traces");
      }
      return traceLoader.loadAllTraces(parseUuid(token));
    }

    if(from != null && to != null) {
      final Multi<Timestamp> allTimestamps = this.timestampLoader.loadAllTimestampsForToken(parseUuid(token), commit);
      //final boolean isSavepoint = from != exact;
      // Transform Multi<Timestamp> to Multi<Trace> with filtered spans
      final Multi<Trace> tracesWithSpansUnfiltered = allTimestamps.select()
          .where(timestamp -> timestamp.epochNano() >= from && timestamp.epochNano() < to) // all traces within the buckets that fulfill the time range
          .onItem().transformToMultiAndConcatenate(
            timestamp -> traceLoader.loadTracesStartingInRange(parseUuid(token), timestamp.epochNano()) // multiple traces may be in the same bucket
          ).select().where(trace -> trace.startTime() < to);

      return tracesWithSpansUnfiltered.onItem().transform(trace -> {
        final List<Span> filteredSpans = trace.spanList().stream()
            .filter(span -> span.startTime() < to && span.startTime() >= exact)
            .collect(Collectors.toList());
        final List<String> filteredSpanIds = filteredSpans.stream()
            .map(span -> span.spanId())
            .collect(Collectors.toList());
  
        final List<Span> orphanSpans = filteredSpans.stream()
             .filter(span -> !filteredSpanIds.contains(span.parentSpanId()))
             .map(orphan -> new Span(orphan.spanId(), "", orphan.startTime(), orphan.endTime(), orphan.methodHash()))
             .collect(Collectors.toList());
        final List<Span> spansWithParents = filteredSpans.stream()
              .filter(span -> filteredSpanIds.contains(span.parentSpanId()))
              .collect(Collectors.toList());
        List<Span> merged = Stream.concat(orphanSpans.stream(), spansWithParents.stream())
              .collect(Collectors.toList());

        Trace traceWithSpansFiltered = new Trace(
          trace.landscapeToken(), trace.traceId(), trace.gitCommitChecksum(), trace.startTime(), 
          trace.endTime(), trace.duration(), trace.overallRequestCount(), 
          trace.traceCount(), merged);
        return traceWithSpansFiltered; 
      });
      //allTimestamps.collect().asList()
    }

    // ATTENTION: For the moment (with only one timestamp being selected), we only filter based on the starting point of traces
    return traceLoader.loadTracesStartingInRange(parseUuid(token), from);
  }

  @GET
  @Path("/{token}/dynamic/{traceid}")
  public Uni<Trace> getDynamicTrace(@PathParam("token") final String token,
      @PathParam("traceid") final String traceId) {
    return traceLoader.loadTrace(parseUuid(token), traceId);
  }

  @DELETE
  @Path("/{token}/trace-data")
  @Operation(summary = "Delete all trace data for a landscape token",
      description =
          "Removes all trace and span data from the database for the given landscape token. "
              + "This includes data from all related tables. Use with caution.")
  @APIResponses({
      @APIResponse(responseCode = "204", description = "Trace data successfully deleted"),
      @APIResponse(responseCode = "400", description = "Invalid token format"),
      @APIResponse(responseCode = "500", description = "Internal server error during deletion")
  })
  public Uni<Response> deleteTraceData(@PathParam("token") final String token) {
    final UUID landscapeToken = parseUuid(token);

    LOGGER.info("Deleting all trace data for landscape token: {}", landscapeToken);

    // Clear the in-memory cache first to ensure new spans can be persisted
    spanProcessor.clearCacheForLandscapeToken(landscapeToken);

    // Delete from all relevant tables
    return deleteFromSpanStructure(landscapeToken)
        .chain(() -> deleteFromTraceByTimeAndSpanByTraceid(landscapeToken))
        .chain(() -> deleteFromSpanCountPerTimeBucket(landscapeToken))
        .map(v -> {
          LOGGER.info("Successfully deleted all trace data for landscape token: {}. "
              + "New spans can now be added and will create fresh data.", landscapeToken);
          return Response.noContent().build();
        })
        .onFailure().recoverWithItem(failure -> {
          LOGGER.error("Failed to delete trace data for landscape token: {}", landscapeToken,
              failure);
          return Response.serverError()
              .entity("{\"error\": \"Failed to delete trace data: " + failure.getMessage() + "\"}")
              .build();
        });
  }

  private Uni<Void> deleteFromSpanStructure(final UUID landscapeToken) {
    final SimpleStatement stmt = SimpleStatement.newInstance(
        "DELETE FROM span_structure WHERE landscape_token = ?", landscapeToken);
    return Uni.createFrom().completionStage(session.executeAsync(stmt))
        .replaceWithVoid();
  }

  private Uni<Void> deleteFromTraceByTimeAndSpanByTraceid(final UUID landscapeToken) {
    // Query trace_by_time to get all partition key values needed for deletion
    final SimpleStatement selectStmt = SimpleStatement.newInstance(
        "SELECT tenth_second_epoch, trace_id, git_commit_checksum FROM trace_by_time "
            + "WHERE landscape_token = ? ALLOW FILTERING",
        landscapeToken);

    // Collect unique partition key values for all tables
    final Set<Long> tenthSecondEpochs = new HashSet<>();
    final Set<String> traceIds = new HashSet<>();
    final Set<String> commitChecksums = new HashSet<>();

    return session.executeReactive(selectStmt)
        .onItem().invoke(row -> {
          tenthSecondEpochs.add(row.getLong("tenth_second_epoch"));
          traceIds.add(row.getString("trace_id"));
          final String checksum = row.getString("git_commit_checksum");
          if (checksum != null) {
            commitChecksums.add(checksum);
          }
        })
        .collect().asList()
        .chain(() -> {
          LOGGER.debug(
              "Found {} trace IDs, {} time buckets, {} commit checksums to delete for token {}",
              traceIds.size(), tenthSecondEpochs.size(), commitChecksums.size(), landscapeToken);

          // Delete from span_by_traceid for each trace_id
          return Multi.createFrom().iterable(traceIds)
              .onItem().transformToUniAndConcatenate(traceId -> {
                final SimpleStatement deleteStmt = SimpleStatement.newInstance(
                    "DELETE FROM span_by_traceid WHERE landscape_token = ? AND trace_id = ?",
                    landscapeToken, traceId);
                return Uni.createFrom().completionStage(session.executeAsync(deleteStmt))
                    .replaceWithVoid();
              })
              .collect().asList()
              .replaceWithVoid();
        })
        .chain(() -> {
          // Delete from trace_by_time for each tenth_second_epoch
          return Multi.createFrom().iterable(tenthSecondEpochs)
              .onItem().transformToUniAndConcatenate(epoch -> {
                final SimpleStatement deleteStmt = SimpleStatement.newInstance(
                    "DELETE FROM trace_by_time WHERE landscape_token = ? "
                        + "AND tenth_second_epoch = ?",
                    landscapeToken, epoch);
                return Uni.createFrom().completionStage(session.executeAsync(deleteStmt))
                    .replaceWithVoid();
              })
              .collect().asList()
              .replaceWithVoid();
        })
        .chain(() -> {
          // Delete from span_count_for_token_and_commit_and_time_bucket for each commit checksum
          return Multi.createFrom().iterable(commitChecksums)
              .onItem().transformToUniAndConcatenate(checksum -> {
                final SimpleStatement deleteStmt = SimpleStatement.newInstance(
                    "DELETE FROM span_count_for_token_and_commit_and_time_bucket "
                        + "WHERE landscape_token = ? AND git_commit_checksum = ?",
                    landscapeToken, checksum);
                return Uni.createFrom().completionStage(session.executeAsync(deleteStmt))
                    .replaceWithVoid();
              })
              .collect().asList()
              .replaceWithVoid();
        });
  }

  private Uni<Void> deleteFromSpanCountPerTimeBucket(final UUID landscapeToken) {
    // Delete from span_count_per_time_bucket_and_token
    // Partition key is just landscape_token, so we can delete directly
    final SimpleStatement deleteTimestampsStmt = SimpleStatement.newInstance(
        "DELETE FROM span_count_per_time_bucket_and_token WHERE landscape_token = ?",
        landscapeToken);

    return Uni.createFrom().completionStage(session.executeAsync(deleteTimestampsStmt))
        .replaceWithVoid();
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
