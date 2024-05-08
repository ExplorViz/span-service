package net.explorviz.span.persistence;

import com.datastax.oss.quarkus.test.CassandraTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import net.explorviz.span.kafka.KafkaTestResource;
import net.explorviz.span.trace.Span;
import net.explorviz.span.trace.Trace;
import net.explorviz.span.trace.TraceLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(CassandraTestResource.class)
public class TraceLoaderIt {

  @Inject
  PersistenceSpanProcessor spanProcessor;

  @Inject
  TraceLoader traceLoader;

  private Span convertPersistenceSpanToSpan(PersistenceSpan ps) {
    return new Span(ps.spanId(), ps.parentSpanId(),
        ps.startTime(), ps.endTime(), String.valueOf(ps.methodHash()));
  }

  @Test
  void testLoadTracesByTimeRange() {

    long startEarly = 1701081827000L;
    long endEarly = 1701081828000L;
    long startExpected = 1701081830000L;
    long endExpected = 1701081831000L;
    long startLate = 1701081843000L;
    long endLate = 1701081844000L;

    final UUID landscapeToken = UUID.randomUUID();

    final String gitCommitChecksum = "testGitCommitChecksum";

    final PersistenceSpan earlySpan =
        new PersistenceSpan(landscapeToken, gitCommitChecksum, "123L", "", "1L", startEarly,
            endEarly, "nodeIp", "host-name", "app-name", "java", 0,
            "net.explorviz.Class.myMethod()",
            "847");

    final PersistenceSpan expectedSpan =
        new PersistenceSpan(landscapeToken, gitCommitChecksum, "456L", "", "2L", startExpected,
            endExpected, "nodeIp", "host-name", "app-name", "java", 0,
            "net.explorviz.Class.myMethod()",
            "847");

    final PersistenceSpan lateSpan =
        new PersistenceSpan(landscapeToken, gitCommitChecksum, "789L", "", "3L", startLate,
            endLate, "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class.myMethod()",
            "847");

    spanProcessor.accept(earlySpan);
    spanProcessor.accept(expectedSpan);
    spanProcessor.accept(lateSpan);

    List<Trace> result =
        traceLoader.loadTracesStartingInRange(landscapeToken, 1701081830000L).collect().asList()
            .await().indefinitely();

    Assertions.assertEquals(1, result.size(), "List of traces has wrong size.");
    Assertions.assertEquals(9, result.get(0).getClass().getDeclaredFields().length,
        "Trace has wrong number of fields.");
    Assertions.assertEquals(1, result.get(0).spanList().size(), "List of spans has wrong size.");
    Assertions.assertEquals(convertPersistenceSpanToSpan(expectedSpan),
        result.get(0).spanList().get(0), "Wrong span in trace.");
  }

}
