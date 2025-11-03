package net.explorviz.span.trace;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.explorviz.span.timestamp.TimestampLoader;

import java.util.Map;
import java.util.UUID;

import org.neo4j.driver.Driver;
import org.neo4j.driver.reactive.ReactiveResult;
import org.neo4j.driver.reactive.ReactiveSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TraceLoader {

  private static final Logger LOGGER = LoggerFactory.getLogger(TraceLoader.class);

  private static final String selectTraceByTime = """
      MATCH (t:TraceByTime)
      WHERE t.landscape_token = $landscape_token
      RETURN t;""";

  private static final String selectAllTraces = """
      MATCH (t:TraceByTime)
      WHERE t.landscape_token = $landscape_token
      AND t.tenth_second_epoch = $tenth_second_epoch
      RETURN t;""";

  private static final String selectSpanByTraceId = """
      MATCH (s:SpanByTraceId)
      WHERE t.landscape_token = $landscape_token
        AND t.trace_id = $trace_id
      RETURN s;""";

  @Inject
  Driver driver;

  static Uni<Void> sessionFinalizer(ReactiveSession session) { 
    return Uni.createFrom().publisher(session.close());
  }

  public Multi<Trace> loadAllTraces(final UUID landscapeToken) {
    LOGGER.atTrace().addArgument(landscapeToken).log("Loading all traces for token {}");

    // TODO: Trace should not contain itself? i.e. filter out parent_span_id = 0
    // TODO: Is from/to inclusive/exclusive?

    return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class), 
        session -> session.executeRead(tx -> {
            var result = tx.run(selectAllTraces, Map.of("landscape_token", landscapeToken.toString()));
            return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
        }))
        .withFinalizer(TraceLoader::sessionFinalizer) 
        .map(Trace::fromRecord)
        .flatMap(trace -> {
          LOGGER.atTrace().addArgument(() -> trace.traceId()).log("Found trace {}");
          return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class), 
              session -> session.executeRead(tx -> {
                  var result = tx.run(selectSpanByTraceId, Map.of("landscape_token", landscapeToken.toString(), "trace_id", trace.traceId()));
                  return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
              }))
              .withFinalizer(TraceLoader::sessionFinalizer) 
              .map(Span::fromRecord)
              .collect().asList().map(spanList -> {
                trace.spanList().addAll(spanList);
                return trace;
              }).toMulti();
        });
  }

  public Multi<Trace> loadTracesStartingInRange(final UUID landscapeToken,
      final long tenthSecondEpoch) {
    LOGGER.atTrace().addArgument(landscapeToken).addArgument(tenthSecondEpoch)
        .log("Loading all traces for token {} in epoch bucket {}");

    // TODO: Trace should not contain itself? i.e. filter out parent_span_id = 0
    // TODO: Is from/to inclusive/exclusive?
    return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class), 
        session -> session.executeRead(tx -> {
            var result = tx.run(selectTraceByTime, Map.of("landscape_token", landscapeToken.toString(), "tenth_second_epoch", tenthSecondEpoch));
            return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
        }))
        .withFinalizer(TraceLoader::sessionFinalizer)
        .map(Trace::fromRecord)
        .flatMap(trace -> {
              LOGGER.atTrace().addArgument(() -> trace.traceId()).log("Found trace {}");

              return Multi.createFrom().resource(() -> driver.session(ReactiveSession.class),
                      session -> session.executeRead(tx -> {
                        var result = tx.run(selectSpanByTraceId,
                            Map.of("landscape_token", landscapeToken.toString(), "trace_id", trace.traceId()));
                        return Multi.createFrom().publisher(result).flatMap(ReactiveResult::records);
                      }))
                  .withFinalizer(TraceLoader::sessionFinalizer)
                  .map(Span::fromRecord)
                  .collect().asList().map(spanList -> {
                    trace.spanList().addAll(spanList);
                    return trace;
                  }).toMulti();
        });
  }

  // TODO: Trace should not contain itself? i.e. filter out parent_span_id = 0

  public Uni<Trace> loadTrace(final UUID landscapeToken, final String traceId) {
    /*
     * LOGGER.atTrace().addArgument(traceId).addArgument(landscapeToken)
     * .log("Loading trace {} for token {}");
     *
     * return session.executeReactive(selectSpanByTraceid.bind(landscapeToken,
     * traceId))
     * .map(Span::fromRow).collect().asList().map(Trace::fromSpanList);
     */
    return Uni.createFrom().nullItem();
  }
}
