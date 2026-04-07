package net.explorviz.span.persistence;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.explorviz.span.messaging.KafkaSpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class PersistenceSpanProcessor implements Consumer<PersistenceSpan> {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceSpanProcessor.class);

  private final AtomicLong lastProcessedSpans = new AtomicLong(0L);
  private final AtomicLong lastExportedSpans = new AtomicLong(0L);
  private final AtomicLong lastFailures = new AtomicLong(0L);

  private final ConcurrentMap<String, Set<String>> knownSpanIdsByLandscape =
      new ConcurrentHashMap<>(1);

  @Inject
  /* default */ KafkaSpanExporter exporter;

  @Override
  public void accept(final PersistenceSpan span) {
    final Set<String> knownSpanIds = knownSpanIdsByLandscape.computeIfAbsent(span.landscapeToken(),
        tokenValue  -> ConcurrentHashMap.newKeySet());
    if (knownSpanIds.add(span.spanId())) {
      exporter.persistSpan(span);
      lastExportedSpans.incrementAndGet();
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
