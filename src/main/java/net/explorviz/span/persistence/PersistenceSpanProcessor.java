package net.explorviz.span.persistence;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.explorviz.span.grpc.GrpcExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class PersistenceSpanProcessor implements Consumer<PersistenceSpan> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceSpanProcessor.class);

  private final AtomicLong lastProcessedSpans = new AtomicLong(0L);
  private final AtomicLong lastExportedSpans = new AtomicLong(0L);
  private final AtomicLong lastFailures = new AtomicLong(0L);

  private final ConcurrentMap<UUID, Set<String>> knownSpanIdsByLandscape =
      new ConcurrentHashMap<>(1);

  @Inject
  /* default */ GrpcExporter exporter;

  @Override
  public void accept(final PersistenceSpan span) {
    final Set<String> knownSpanIds = knownSpanIdsByLandscape.computeIfAbsent(span.landscapeToken(),
        uuid -> ConcurrentHashMap.newKeySet());
    if (knownSpanIds.add(span.spanId())) {
      try {
        exporter.persistSpan(span);
        lastExportedSpans.incrementAndGet();
      } catch (Exception e) {
        LOGGER.atError().addArgument(e.getMessage()).log("Failed to export span via gRPC: {}", e);
        lastFailures.incrementAndGet();
      }
    }

    lastProcessedSpans.incrementAndGet();
  }

  @Scheduled(every = "{explorviz.log.span.interval}")
  public void logStatus() {
    final long processedSpans = this.lastProcessedSpans.getAndSet(0);
    final long exportedSpans = this.lastExportedSpans.getAndSet(0);
    LOGGER.atDebug().addArgument(processedSpans).addArgument(exportedSpans)
        .log("Processed {} spans, exported {} spans.");
    final long failures = this.lastFailures.getAndSet(0);
    if (failures != 0) {
      LOGGER.atWarn().addArgument(failures).log("Data loss occurred. {} span exports failed.");
    }
  }
}
