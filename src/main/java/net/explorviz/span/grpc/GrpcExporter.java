package net.explorviz.span.grpc;

import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.proto.SpanData;
import net.explorviz.span.proto.SpanDataService;


@ApplicationScoped
public class GrpcExporter {

  @GrpcClient("spanDataGrpcClient")
  SpanDataService spanDataGrpcClient;

  public void persistSpan(PersistenceSpan span) {
    SpanData spanData = SpanData.newBuilder()
        .setId(span.spanId())
        .setStartTime(span.startTime())
        .setEndTime(span.endTime())
        .build();

    spanDataGrpcClient.persistSpan(spanData)
        .subscribe().with(
            reply -> System.out.println("Server replied: " + reply),
            Throwable::printStackTrace
        );
  }

}
