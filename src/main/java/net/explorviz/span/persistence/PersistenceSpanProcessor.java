package net.explorviz.span.persistence;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.eclipse.microprofile.context.ThreadContext;
import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.ResultCursor;
import org.neo4j.driver.summary.ResultSummary;
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

  private static final String insertSpanStructureStatement = """
      MERGE (ss:SpanStructure {
        landscape_token: $landscape_token,
        method_hash: $method_hash,
        timestamp: $timestamp
      })
      SET ss.node_ip_address = $node_ip_address,
          ss.host_name = $host_name,
          ss.application_name = $application_name,
          ss.application_language = $application_language,
          ss.application_instance = $application_instance,
          ss.method_fqn = $method_fqn,
          ss.time_seen = $start_time,
          ss.k8s_pod_name = $k8s_pod_name,
          ss.k8s_node_name = $k8s_node_name,
          ss.k8s_namespace = $k8s_namespace,
          ss.k8s_deployment_name = $k8s_deployment_name
      RETURN ss;""";

  private static final String insertSpanByTraceIdStatement = """
      MERGE (s:SpanByTraceId {
        landscape_token: $landscape_token,
        trace_id: $trace_id,
        span_id: $span_id
      })
      SET s.parent_span_id = $parent_span_id,
          s.start_time = $start_time,
          s.end_time = $end_time,
          s.method_hash = $method_hash;""";

  private static final String insertTraceByTimeStatement = """
      MERGE (t:TraceByTime {
        landscape_token: $landscape_token,
        trace_id: $trace_id
      })
      SET t.git_commit_checksum = $git_commit_checksum,
          t.tenth_second_epoch = $tenth_second_epoch,
          t.start_time = $start_time,
          t.end_time = $end_time;""";

  private static final String updateSpanBucketCounter = """
      MERGE (sc:SpanCount {
        landscape_token: $landscape_token,
        tenth_second_epoch: $tenth_second_epoch
      })
      ON CREATE SET sc.span_count = 0
      SET sc.span_count = sc.span_count + 1;""";

  private static final String updateSpanBucketCounterForCommits = """
      MERGE (sc:SpanCountCommit {
        landscape_token: $landscape_token,
        git_commit_checksum: $git_commit_checksum,
        tenth_second_epoch: $tenth_second_epoch
      })
      ON CREATE SET sc.span_count = 0
      SET sc.span_count = sc.span_count + 1;""";

  @Inject
  private Driver driver;

  @Inject
  private ThreadContext threadContext;

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

    updateSpanBucketCounter(span);

    lastProcessedSpans.incrementAndGet();
  }

  private void updateSpanBucketCounter(final PersistenceSpan span) {
    final long tenSecondBucket = span.startTime() - (span.startTime() % 10_000);

    Map<String, Object> params1 = Map.of(
        "landscape_token", span.landscapeToken().toString(), "tenth_second_epoch", tenSecondBucket);

    Map<String, Object> params2 = Map.of(
        "landscape_token", span.landscapeToken().toString(),
        "git_commit_checksum", span.gitCommitChecksum(),
        "tenth_second_epoch", tenSecondBucket);

    AsyncSession session = driver.session(AsyncSession.class);

    CompletionStage<ResultSummary> cs = session.executeWriteAsync(tx -> tx
        .runAsync(updateSpanBucketCounter, params1)
        .thenCompose(ResultCursor::consumeAsync)
        .thenCompose(rs -> tx.runAsync(updateSpanBucketCounterForCommits, params2))
        .thenCompose(ResultCursor::consumeAsync));

    threadContext.withContextCapture(cs)
        .thenCompose(summary -> session.closeAsync());
  }

  private void insertSpanStructure(final PersistenceSpan span) {
    Map<String, Object> params = Map.ofEntries(
        Map.entry("landscape_token", span.landscapeToken().toString()),
        Map.entry("method_hash", span.methodHash()),
        Map.entry("node_ip_address", span.nodeIpAddress()),
        Map.entry("host_name", span.hostName()),
        Map.entry("application_name", span.applicationName()),
        Map.entry("application_language", span.applicationLanguage()),
        Map.entry("application_instance", span.applicationInstance()),
        Map.entry("method_fqn", span.methodFqn()),
        Map.entry("time_seen", span.startTime()),
        Map.entry("k8s_pod_name", span.k8sPodName()),
        Map.entry("k8s_node_name", span.k8sNodeName()),
        Map.entry("k8s_namespace", span.k8sNamespace()),
        Map.entry("k8s_deployment_name", span.k8sDeploymentName()),
        Map.entry("timestamp", Instant.now().toEpochMilli()));

    AsyncSession session = driver.session(AsyncSession.class);
    CompletionStage<ResultSummary> cs = session.executeWriteAsync(tx -> tx
        .runAsync(insertSpanStructureStatement, params)
        .thenCompose(ResultCursor::consumeAsync)
        .whenComplete((result, failure) -> {
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
        }));

    threadContext.withContextCapture(cs)
        .thenCompose(summary -> session.closeAsync());
  }

  private void insertSpanDynamic(final PersistenceSpan span) {
    Map<String, Object> params = Map.ofEntries(
        Map.entry("landscape_token", span.landscapeToken().toString()),
        Map.entry("trace_id", span.traceId()),
        Map.entry("span_id", span.spanId()),
        Map.entry("parent_span_id", span.parentSpanId()),
        Map.entry("start_time", span.startTime()),
        Map.entry("end_time", span.endTime()),
        Map.entry("method_hash", span.methodHash()));

    AsyncSession session = driver.session(AsyncSession.class);
    CompletionStage<ResultSummary> cs = session.executeWriteAsync(tx -> tx
        .runAsync(insertSpanByTraceIdStatement, params)
        .thenCompose(ResultCursor::consumeAsync)
        .whenComplete((result, failure) -> {
          if (failure == null) {
            LOGGER.atTrace().addArgument(span::methodHash).addArgument(span::methodFqn)
                .addArgument(span::traceId)
                .log("Saved new dynamic span with method_hash={}, method_fqn={}, trace_id={}");
          } else {
            lastFailures.incrementAndGet();
            LOGGER.error("Could not persist trace by time", failure);
          }
        }));

    threadContext.withContextCapture(cs)
        .thenCompose(summary -> session.closeAsync());
  }

  private void insertTrace(final PersistenceSpan span) {
    final long tenSecondBucket = span.startTime() - (span.startTime() % 10_000);

    Map<String, Object> params = Map.ofEntries(
        Map.entry("landscape_token", span.landscapeToken().toString()),
        Map.entry("git_commit_checksum", span.gitCommitChecksum()),
        Map.entry("tenth_second_epoch", tenSecondBucket),
        Map.entry("start_time", span.startTime()),
        Map.entry("end_time", span.endTime()),
        Map.entry("trace_id", span.traceId()));

    AsyncSession session = driver.session(AsyncSession.class);
    CompletionStage<ResultSummary> cs = session.executeWriteAsync(tx -> tx
        .runAsync(insertTraceByTimeStatement, params)
        .thenCompose(ResultCursor::consumeAsync)
        .whenComplete((result, failure) -> {
          if (failure == null) {
            lastSavedTraces.incrementAndGet();
            LOGGER.atTrace().addArgument(span::landscapeToken).addArgument(span::traceId)
                .addArgument(tenSecondBucket)
                .log("Saved new trace with token={}, trace_id={}, and ten second epoch bucket={}");
          } else {
            lastFailures.incrementAndGet();
            LOGGER.error("Could not persist trace by time", failure);
          }
        }));

    threadContext.withContextCapture(cs).thenCompose(summary -> session.closeAsync());
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
