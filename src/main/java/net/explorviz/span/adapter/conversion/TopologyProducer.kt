package net.explorviz.span.adapter.conversion

import com.google.protobuf.InvalidProtocolBufferException
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.proto.trace.v1.Span
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import java.util.concurrent.atomic.AtomicInteger
import net.explorviz.span.adapter.service.converter.SpanConverterImpl
import net.explorviz.avro.EventType
import net.explorviz.avro.TokenEvent
import net.explorviz.span.adapter.service.validation.SpanValidator
import net.explorviz.span.persistence.PersistenceSpan
import net.explorviz.span.persistence.PersistenceSpanProcessor
import net.explorviz.span.persistence.SpanConverter
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.slf4j.Logger
import org.slf4j.LoggerFactory


/** Builds a KafkaStream topology instance with all its transformers. Entry point of the stream analysis. */
@ApplicationScoped
class TopologyProducer {

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(TopologyProducer::class.java)
    }

    private val lastReceivedSpans = AtomicInteger(0)
    private val lastInvalidSpans = AtomicInteger(0)

    @ConfigProperty(name = "explorviz.kafka-streams.topics.in") lateinit var inTopic: String

    @ConfigProperty(name = "explorviz.kafka-streams.topics.out.spans") lateinit var spansOutTopic: String

    @ConfigProperty(name = "explorviz.kafka-streams.topics.in.tokens") lateinit var tokensInTopic: String

    @ConfigProperty(name = "explorviz.kafka-streams.topics.out.tokens-table") lateinit var tokensOutTopic: String

    @Inject lateinit var validator: SpanValidator

    @Inject lateinit var spanAvroSerde: SpecificAvroSerde<net.explorviz.avro.Span>

    @Inject lateinit var tokenEventAvroSerde: SpecificAvroSerde<TokenEvent>

    @Inject lateinit var spanConverter: SpanConverterImpl

    @Inject
    lateinit var persistenceSpanConverter: SpanConverter


    @Inject
    lateinit var persistenceProcessor: PersistenceSpanProcessor


    @Produces
    fun buildTopology(): Topology {
        val builder = StreamsBuilder()

        // BEGIN Conversion Stream
        val spanByteStream: KStream<ByteArray, ByteArray> =
            builder.stream(inTopic, Consumed.with(Serdes.ByteArray(), Serdes.ByteArray()))

        val spanStream: KStream<ByteArray, Span> =
            spanByteStream.flatMapValues { data ->
                try {
                    val spanList = mutableListOf<Span>()
                    ExportTraceServiceRequest.parseFrom(data).resourceSpansList.forEach { resourceSpans ->
                        resourceSpans.scopeSpansList.forEach { scopeSpans -> spanList.addAll(scopeSpans.spansList) }
                    }
                    lastReceivedSpans.addAndGet(spanList.size)
                    spanList
                } catch (e: InvalidProtocolBufferException) {
                    LOGGER.trace("Invalid protocol buffer: ${e.message}")
                    emptyList()
                }
            }

        // Validate Spans
        val validSpanStream: KStream<ByteArray, Span> =
            spanStream.flatMapValues { value ->
                if (!validator.isValid(value)) {
                    lastInvalidSpans.incrementAndGet()
                    emptyList()
                } else {
                    listOf(value)
                }
            }

        // Convert to Span Structure
        val explorvizSpanStream: KStream<String, net.explorviz.avro.Span> =
            validSpanStream.map { _, value ->
                val span = spanConverter.fromOpenTelemetrySpan(value)
                KeyValue(span.landscapeToken, span)
            }

        // Map to our more space-efficient PersistenceSpan format
        // ToDo: Combine with previous step
        val persistenceStream: KStream<String, PersistenceSpan> = explorvizSpanStream.mapValues(
            this.persistenceSpanConverter,
        )

        persistenceStream.foreach { _ : String?, span: PersistenceSpan? ->
            persistenceProcessor.accept(
                span,
            )
        }
        // END Conversion Stream

        // BEGIN Token Stream
        builder
            .stream(tokensInTopic, Consumed.with(Serdes.String(), tokenEventAvroSerde))
            .filter { key, value ->
                LOGGER.trace("Received token event for token value $key with event $value")
                value == null || value.type == EventType.CREATED
            }
            .to(tokensOutTopic, Produced.with(Serdes.String(), tokenEventAvroSerde))

        builder.globalTable(
            tokensOutTopic,
            Materialized.`as`<String, TokenEvent, KeyValueStore<Bytes, ByteArray>>("token-events-global-store")
                .withKeySerde(Serdes.String())
                .withValueSerde(tokenEventAvroSerde),
        )
        // END Token Stream

        return builder.build()
    }

    @Scheduled(every = "{explorviz.log.span.interval}")
    fun logStatus() {
        val spans = lastReceivedSpans.getAndSet(0)
        val invalidSpans = lastInvalidSpans.getAndSet(0)
        LOGGER.debug("Received $spans spans: ${spans - invalidSpans} valid, $invalidSpans invalid")
    }
}
