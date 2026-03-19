package net.explorviz.span.grpc;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import net.explorviz.span.adapter.service.converter.DefaultAttributeValues;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.proto.SpanData;
import net.explorviz.span.proto.SpanDataService;
import net.explorviz.span.proto.SpanDataServiceGrpc;

@ApplicationScoped
public class GrpcExporter {

  @GrpcClient("spanDataGrpcClient")
  /* default */ SpanDataServiceGrpc.SpanDataServiceBlockingStub spanDataGrpcClient;

  public void persistSpan(PersistenceSpan span) {
    final SpanData.Builder spanDataBuilder =
        SpanData.newBuilder().setSpanId(span.spanId()).setParentId(span.parentSpanId())
            .setTraceId(span.traceId()).setLandscapeTokenId(span.landscapeToken().toString())
            .setStartTime(span.startTime()).setEndTime(span.endTime())
            .setApplicationName(span.applicationName()).setFunctionName(span.functionName())
            .setFilePath(span.filePath());

    if (!span.className().isBlank()) {
      spanDataBuilder.setClassName(span.className());
    }

    if (!span.gitCommitChecksum().equals(DefaultAttributeValues.DEFAULT_GIT_COMMIT_CHECKSUM)
        && !span.gitCommitChecksum().isBlank()) {
      spanDataBuilder.setCommitHash(span.gitCommitChecksum());
    }

    spanDataGrpcClient.persistSpan(spanDataBuilder.build());
  }

}
