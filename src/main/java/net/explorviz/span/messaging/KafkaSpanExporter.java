package net.explorviz.span.messaging;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.explorviz.persistence.avro.SpanData;
import net.explorviz.span.adapter.service.converter.DefaultAttributeValues;
import net.explorviz.span.persistence.PersistenceSpan;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class KafkaSpanExporter {

  @Inject
  @Channel("explorviz-spans")
  Emitter<SpanData> emitter;

  public void persistSpan(PersistenceSpan span) {
    SpanData spanData = mapToAvro(span);

    emitter.send(
        Message.of(spanData)
            .addMetadata(
                OutgoingKafkaRecordMetadata.<String>builder().withKey(span.traceId()).build()));
  }

  private SpanData mapToAvro(PersistenceSpan span) {
    final boolean hasGitCommitChecksum =
        !span.gitCommitChecksum().equals(DefaultAttributeValues.DEFAULT_GIT_COMMIT_CHECKSUM)
            && !span.gitCommitChecksum().isBlank();

    return SpanData.newBuilder()
        .setSpanId(span.spanId())
        .setParentId(span.parentSpanId())
        .setTraceId(span.traceId())
        .setLandscapeTokenId(span.landscapeToken())
        .setStartTime(span.startTime())
        .setEndTime(span.endTime())
        .setApplicationName(span.applicationName())
        .setLanguage(span.applicationLanguage())
        .setFunctionName(span.functionName())
        .setFilePath(span.filePath())
        .setClassName(span.className().isBlank() ? null : span.className())
        .setCommitHash(hasGitCommitChecksum ? span.gitCommitChecksum() : null)
        .build();
  }
}
