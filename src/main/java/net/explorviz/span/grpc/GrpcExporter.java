package net.explorviz.span.grpc;

import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.proto.SpanData;
import net.explorviz.span.proto.SpanDataService;

@ApplicationScoped
public class GrpcExporter {

  @GrpcClient("spanDataGrpcClient")
  private SpanDataService spanDataGrpcClient;

  public Uni<Empty> persistSpan(PersistenceSpan span) {
    final SpanData spanData = SpanData.newBuilder()
        .setSpanId(span.spanId())
        .setParentId(span.parentSpanId())
        .setTraceId(span.traceId())
        .setLandscapeTokenId(span.landscapeToken().toString())
        .setStartTime(span.startTime())
        .setEndTime(span.endTime())
        .setApplicationName(span.applicationName())
        .setFunctionFqn(span.methodFqn())
        .build();

    return spanDataGrpcClient.persistSpan(spanData);
  }

}
