package net.explorviz.span.persistence;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class PersistenceSpanProcessor implements Consumer<PersistenceSpan> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceSpanProcessor.class);

  private final AtomicLong lastProcessedSpans = new AtomicLong(0L);
  private final AtomicLong lastSavedTraces = new AtomicLong(0L);
  private final AtomicLong lastSavesSpanStructures = new AtomicLong(0L);
  private final AtomicLong lastFailures = new AtomicLong(0L);

  private final ConcurrentMap<UUID, Set<String>> knownHashesByLandscape = new ConcurrentHashMap<>(
      1);

  private final QuarkusCqlSession session;

  //private final PreparedStatement insertSpanByTimeStatement;
  private final PreparedStatement insertSpanByTraceidStatement;
  //private final PreparedStatement insertTraceByHashStatement;
  private final PreparedStatement insertTraceByTimeStatement;
  private final PreparedStatement insertSpanStructureStatement;

  @Inject
  public PersistenceSpanProcessor(final QuarkusCqlSession session) {
    this.session = session;

    /*this.insertSpanByTimeStatement = session.prepare(
              "INSERT INTO span_by_time "
            + "(landscape_token, start_time_s, start_time_ns, method_hash, span_id, trace_id) "
            + "VALUES (?, ?, ?, ?, ?, ?)");*/
    this.insertSpanByTraceidStatement = session.prepare("INSERT INTO span_by_traceid "
        + "(landscape_token, trace_id, span_id, parent_span_id, start_time, "
        + "end_time, method_hash) " + "VALUES (?, ?, ?, ?, ?, ?, ?)");
    /*this.insertTraceByHashStatement = session.prepare(
              "INSERT INTO trace_by_hash "
            + "(landscape_token, method_hash, time_bucket, trace_id) "
            + "VALUES (?, ?, ?, ?)");*/
    this.insertTraceByTimeStatement = session.prepare("INSERT INTO trace_by_time "
        + "(landscape_token, start_time, end_time, trace_id) "
        + "VALUES (?, ?, ?, ?)");
    this.insertSpanStructureStatement = session.prepare("INSERT INTO span_structure "
        + "(landscape_token, method_hash, node_ip_address, application_name, application_language, "
        + "application_instance, method_fqn, time_seen) " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
        + "USING TIMESTAMP ?");
  }

  @Override
  public void accept(final PersistenceSpan span) {

    final Set<String> knownHashes = knownHashesByLandscape.computeIfAbsent(span.landscapeToken(),
        uuid -> ConcurrentHashMap.newKeySet());

    if (knownHashes.add(span.methodHash())) {
      insertSpanStructure(span);
    }

    // TODO: We should probably only insert spans
    //  after corresponding span_structure has been inserted?

    if (span.parentSpanId().isEmpty()) {
      insertTrace(span);
    }

    insertSpanDynamic(span);

    lastProcessedSpans.incrementAndGet();
  }

  private void insertSpanStructure(final PersistenceSpan span) {
    final BoundStatement stmtStructure =
        insertSpanStructureStatement.bind(span.landscapeToken(), span.methodHash(),
            span.nodeIpAddress(), span.applicationName(), span.applicationLanguage(),
            span.applicationInstance(), span.methodFqn(), span.startTime(),
            Instant.now().toEpochMilli());

    session.executeAsync(stmtStructure).whenComplete((result, failure) -> {
      if (failure == null) {
        LOGGER.atTrace().addArgument(span::methodHash).addArgument(span::methodFqn)
            .log("Saved new structure span with method_hash={}, method_fqn={}");
        lastSavesSpanStructures.incrementAndGet();
      } else {
        lastFailures.incrementAndGet();
        LOGGER.atError().addArgument(span::methodHash)
            .log("Could not persist structure span with hash {}, removing from cache");
        knownHashesByLandscape.get(span.landscapeToken()).remove(span.methodHash());
      }
    });
  }

  private void insertSpanDynamic(final PersistenceSpan span) {
    /*final BoundStatement stmtByTime = insertSpanByTimeStatement.bind(
        span.landscapeToken(),
        span.getStartTimeSeconds(),
        span.getStartTimeNanos(),
        span.methodHash(),
        span.spanId(),
        span.traceId()
    );*/
    // "(landscape_token, trace_id, span_id, parent_span_id, start_time_s, start_time_ns, "
    //            + "end_time_s, end_time_ns, method_hash) "
    final BoundStatement stmtByTraceid =
        insertSpanByTraceidStatement.bind(span.landscapeToken(), span.traceId(), span.spanId(),
            span.parentSpanId(), span.startTime(), span.endTime(), span.methodHash());
    /*final BoundStatement stmtByHash = insertTraceByHashStatement.bind(
        span.landscapeToken(),
        span.methodHash(),
        span.getStartTimeBucket(),
        span.traceId()
    );*/

    /*session.executeAsync(stmtByTime).exceptionally(failure -> {
      lastFailures.incrementAndGet();
      //LOGGER.error("Could not persist span by time", failure);
      return null;
    });*/
    session.executeAsync(stmtByTraceid).whenComplete((result, failure) -> {
      if (failure == null) {
        LOGGER.atTrace().addArgument(span::methodHash).addArgument(span::methodFqn)
            .addArgument(span::traceId)
            .log("Saved new dynamic span with method_hash={}, method_fqn={}, trace_id={}");
      } else {
        lastFailures.incrementAndGet();
        //LOGGER.error("Could not persist trace by time", failure);
      }
    });
    /*session.executeAsync(stmtByHash).exceptionally(failure -> {
      lastFailures.incrementAndGet();
      //LOGGER.error("Could not persist trace by hashcode", failure);
      return null;
    });*/
  }

  private void insertTrace(final PersistenceSpan span) {
    final BoundStatement stmtByTime = insertTraceByTimeStatement.bind(span.landscapeToken(),
        span.startTime(), span.endTime(), span.traceId());

    session.executeAsync(stmtByTime).whenComplete((result, failure) -> {
      if (failure == null) {
        lastSavedTraces.incrementAndGet();
        LOGGER.atTrace().addArgument(span::landscapeToken).addArgument(span::traceId)
            .addArgument(span::traceId).log("Saved new trace with token={}, trace_id={}");
      } else {
        lastFailures.incrementAndGet();
        //LOGGER.error("Could not persist trace by time", failure);
      }
    });
  }

  @Scheduled(every = "{explorviz.log.span.interval}")
  public void logStatus() {
    final long processedSpans = this.lastProcessedSpans.getAndSet(0);
    final long savedTraces = this.lastSavedTraces.getAndSet(0);
    final long savesSpanStructures = this.lastSavesSpanStructures.getAndSet(0);
    LOGGER.atDebug().addArgument(processedSpans).addArgument(savedTraces)
        .addArgument(savesSpanStructures)
        .log("Processed {} spans, inserted {} traces and {} span structures.");
    final long failures = this.lastFailures.getAndSet(0);
    if (failures != 0) {
      LOGGER.atWarn().addArgument(failures).log("Data loss occured. {} inserts failed");
    }
  }
}
