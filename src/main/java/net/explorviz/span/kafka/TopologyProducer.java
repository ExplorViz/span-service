package net.explorviz.span.kafka;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicInteger;
import net.explorviz.avro.Span;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.persistence.PersistenceSpanProcessor;
import net.explorviz.span.persistence.SpanConverter;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class TopologyProducer {

  private static final Logger LOGGER = LoggerFactory.getLogger(TopologyProducer.class);

  private final AtomicInteger lastReceivedSpans = new AtomicInteger(0);

  @ConfigProperty(name = "explorviz.kafka-streams.topics.in")
  /* default */ String inTopic;

  @ConfigProperty(name = "explorviz.kafka-streams.discard")
  /* default */ boolean discard;

  @Inject
  /* default */ Serde<Span> spanSerde;

  @Inject
  /* default */ SpanConverter spanConverter;

  @Inject
  /* default */ PersistenceSpanProcessor persistenceProcessor;

  @Produces
  public Topology buildTopology() {
    final StreamsBuilder builder = new StreamsBuilder();

    final KStream<String, Span> spanStream = builder.stream(this.inTopic,
        Consumed.with(Serdes.String(), this.spanSerde));

    spanStream.foreach((token, span) -> this.lastReceivedSpans.incrementAndGet());

    if (LOGGER.isTraceEnabled()) {
      spanStream.foreach(
          (token, span) -> LOGGER.trace("Received span: Landscape {}, Trace {}, Span {}", token,
              span.getTraceId(), span.getSpanId()));
    }

    if (this.discard) {
      return builder.build();
    }

    // Map to our more space-efficient PersistenceSpan format
    // TODO: Move format improvements to adapter-service
    final KStream<String, PersistenceSpan> persistenceStream = spanStream.mapValues(
        this.spanConverter);

    if (LOGGER.isTraceEnabled()) {
      persistenceStream.foreach(
          (token, span) -> LOGGER.trace("Span {} has hash code {}: {}", span.spanId(),
              span.methodHash(), span.methodFqn()));
    }

    persistenceStream.foreach((token, span) -> persistenceProcessor.accept(span));

    return builder.build();
  }

  @Scheduled(every = "{explorviz.log.span.interval}")
  public void logStatus() {
    final int receivedSpans = this.lastReceivedSpans.getAndSet(0);
    LOGGER.atDebug().addArgument(receivedSpans).log("Received {} spans.");
  }
}
