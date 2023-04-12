package net.explorviz.span.service.processor;

import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.quarkus.runtime.api.session.QuarkusCqlSession;
import io.quarkus.scheduler.Scheduled;
import net.explorviz.span.persistence.PersistenceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@ApplicationScoped
public class PersistenceSpanProcessor implements Consumer<PersistenceSpan> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceSpanProcessor.class);

    private final AtomicLong lastProcessedSpans = new AtomicLong(0L);
    private final AtomicLong lastSavedTraces = new AtomicLong(0L);
    private final AtomicLong lastSavesSpanStructures = new AtomicLong(0L);
    private final AtomicLong lastFailures = new AtomicLong(0L);

    private final ConcurrentMap<UUID, Set<Long>> knownHashesByLandscape = new ConcurrentHashMap<>(1);

    private final QuarkusCqlSession session;

    private final PreparedStatement insertSpanByTime;
    private final PreparedStatement insertSpanByTraceid;
    private final PreparedStatement insertTraceByHashcode;
    private final PreparedStatement insertTraceByTime;
    private final PreparedStatement insertSpanStructure;

    public PersistenceSpanProcessor(QuarkusCqlSession session) {
        this.session = session;

        this.insertSpanByTime = session.prepare(
            "INSERT INTO span_by_time "
                + "(landscape_token, start_time_s, start_time_ns, hash_code, span_id, trace_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)");
        this.insertSpanByTraceid = session.prepare(
            "INSERT INTO span_by_traceid "
                + "(landscape_token, trace_id, span_id, parent_span_id, start_time, end_time, hash_code) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)");
        this.insertTraceByHashcode = session.prepare(
            "INSERT INTO trace_by_hashcode "
                + "(landscape_token, hash_code, time_bucket, trace_id) "
                + "VALUES (?, ?, ?, ?)");
        this.insertTraceByTime = session.prepare(
            "INSERT INTO trace_by_time "
                + "(landscape_token, start_time_s, start_time_ns, trace_id) "
                + "VALUES (?, ?, ?, ?)");
        this.insertSpanStructure = session.prepare(
            "INSERT INTO span_structure "
                + "(landscape_token, hash_code, host_ip_address, application_name, application_language, instance_id, "
                + "method_fqn, time_seen) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "USING TIMESTAMP ?");
    }

    @Override
    public void accept(PersistenceSpan span) {
        Set<Long> knownHashes = knownHashesByLandscape.computeIfAbsent(span.landscapeToken(),
            uuid -> ConcurrentHashMap.newKeySet());

        if (knownHashes.add(span.methodHashCode())) {
            LOGGER.debug("Inserting new structure with hash_code={}, method_fqn={}", span.methodHashCode(), span.methodFqn());
            insertSpanStructure(span);
        }

        if (span.parentSpanId() == 0) {
            insertTrace(span);
        }

        insertSpan(span);

        lastProcessedSpans.incrementAndGet();
    }

    private void insertSpan(PersistenceSpan span) {
        BoundStatement stmtByTime = insertSpanByTime.bind(
            span.landscapeToken(), span.getStartTimeSeconds(), span.getStartTimeNanos(), span.methodHashCode(),
            span.spanId(), span.traceId()
        );
        BoundStatement stmtByTraceid = insertSpanByTraceid.bind(
            span.landscapeToken(), span.traceId(), span.spanId(), span.parentSpanId(), span.startTime(), span.endTime(),
            span.methodHashCode()
        );
        BoundStatement stmtByHashcode = insertTraceByHashcode.bind(
            span.landscapeToken(), span.methodHashCode(), span.getStartTimeBucket(), span.traceId()
        );

        session.executeAsync(stmtByTime).exceptionally(failure -> {
            lastFailures.incrementAndGet();
            //LOGGER.error("Could not persist span by time", failure);
            return null;
        });
        session.executeAsync(stmtByTraceid).exceptionally(failure -> {
            lastFailures.incrementAndGet();
            //LOGGER.error("Could not persist span by traceid", failure);
            return null;
        });
        session.executeAsync(stmtByHashcode).exceptionally(failure -> {
            lastFailures.incrementAndGet();
            //LOGGER.error("Could not persist trace by hashcode", failure);
            return null;
        });
    }

    private void insertTrace(PersistenceSpan span) {
        BoundStatement stmtByTime = insertTraceByTime.bind(
            span.landscapeToken(), span.getStartTimeSeconds(), span.getStartTimeNanos(), span.traceId()
        );

        session.executeAsync(stmtByTime).whenComplete((result, failure) -> {
            if (failure == null) {
                lastSavedTraces.incrementAndGet();
            } else {
                lastFailures.incrementAndGet();
                //LOGGER.error("Could not persist trace by time", failure);
            }
        });
    }

    private long computeStructureWriteTimestamp(PersistenceSpan span) {
        return Integer.MAX_VALUE - span.getStartTimeSeconds(); // TODO
    }

    private void insertSpanStructure(PersistenceSpan span) {
        BoundStatement stmtStructure = insertSpanStructure.bind(
            span.landscapeToken(), span.methodHashCode(), span.hostIpAddress(), span.appName(), span.appLanguage(),
            span.appInstanceId(), span.methodFqn(), span.startTime(), computeStructureWriteTimestamp(span)
        );

        session.executeAsync(stmtStructure).whenComplete((result, failure) -> {
            if (failure == null) {
                lastSavesSpanStructures.incrementAndGet();
            } else {
                lastFailures.incrementAndGet();
                LOGGER.error("Could not persist structure", failure);
            }
        });
    }

    @Scheduled(every = "{explorviz.log.span.interval}")
    public void logStatus() {
        final long processedSpans = this.lastProcessedSpans.getAndSet(0);
        final long savedTraces = this.lastSavedTraces.getAndSet(0);
        final long savesSpanStructures = this.lastSavesSpanStructures.getAndSet(0);
        LOGGER.debug("Processed {} spans, inserted {} traces and {} span structures.",
            processedSpans, savedTraces, savesSpanStructures);

        final long failures = this.lastFailures.getAndSet(0);
        if (failures != 0) {
            LOGGER.warn("Data loss occured. {} inserts failed", failures);
        }
    }
}
