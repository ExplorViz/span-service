package net.explorviz.span.api;

import static io.restassured.RestAssured.given;

import com.datastax.oss.quarkus.test.CassandraTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import net.explorviz.span.kafka.KafkaTestResource;
import net.explorviz.span.landscape.Application;
import net.explorviz.span.landscape.Landscape;
import net.explorviz.span.landscape.Method;
import net.explorviz.span.landscape.Node;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.persistence.PersistenceSpanProcessor;
import net.explorviz.span.trace.Trace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(CassandraTestResource.class)
public class LandscapeResourceIt {

  @Inject
  PersistenceSpanProcessor spanProcessor;

  final String gitCommitChecksum = "testGitCommitChecksum";

  @Test
  void testLoadAllStructureSpans() {
    final long startEarly = 1701081827000L;
    final long endEarly = 1701081828000L;
    final long startExpected = 1701081830000L;
    final long endExpected = 1701081831000L;
    final long startLate = 1701081833000L;
    final long endLate = 1701081834000L;

    final UUID uuidExpected = UUID.randomUUID();

    final PersistenceSpan differentTokenSpan = new PersistenceSpan(
        UUID.randomUUID(), gitCommitChecksum, "123L", "", "1L", startEarly,
        endEarly, "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class.myMethod()", "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "123L", "", "1L", startEarly, endEarly, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan secondOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "789L", "", "3L", startLate, endLate, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan otherSpan = new PersistenceSpan(uuidExpected, "456L", gitCommitChecksum,
        "0L", "", startExpected, endExpected, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName, "321",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(differentTokenSpan);
    spanProcessor.accept(firstOccurenceSpan);
    spanProcessor.accept(secondOccurenceSpan);
    spanProcessor.accept(otherSpan);

    final Response response = given().pathParam("token", uuidExpected).when()
        .get("/v2/landscapes/{token}/structure");

    final Landscape result = response.getBody().as(Landscape.class);

    final List<Node> node = result.nodes();

    Assertions.assertEquals(1, node.size());
    Assertions.assertEquals("host-name", node.get(0).hostName());
    Assertions.assertEquals("nodeIp", node.get(0).ipAddress());

    final List<Application> applications = result.nodes().get(0).applications();

    Assertions.assertEquals(1, applications.size());
    Assertions.assertEquals("app-name", applications.get(0).name());
    Assertions.assertEquals("java", applications.get(0).language());
    Assertions.assertEquals(0, applications.get(0).instanceId());

    final List<Method> resultMethodList = applications.get(0).packages()
        .get(0).subPackages().get(0).classes().get(0).methods();

    Assertions.assertEquals(2, resultMethodList.size());
    Assertions.assertEquals(otherMethodName, resultMethodList.get(0).name());
    Assertions.assertEquals(duplicateMethodName, resultMethodList.get(1).name());
  }

  @Test
  void testLoadStructureSpansByTimeRange() {
    final long startEarly = 17010818270000L;
    final long endEarly = 1701081828000L;
    final long startExpected = 1701081830000L;
    final long endExpected = 1701081831000L;
    final long startLate = 1701081833000L;
    final long endLate = 1701081834000L;

    final UUID uuidExpected = UUID.randomUUID();

    final PersistenceSpan differentTokenSpan = new PersistenceSpan(
        UUID.randomUUID(), gitCommitChecksum, "123L", "", "1L", startEarly,
        endEarly, "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class.myMethod()", "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "123L", "", "1L", startEarly, endEarly, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan secondOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "789L", "", "3L", startLate, endLate, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan otherSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum, "456L",
        "", "2L", startExpected, endExpected, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName, "321",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(differentTokenSpan);
    spanProcessor.accept(firstOccurenceSpan);
    spanProcessor.accept(secondOccurenceSpan);
    spanProcessor.accept(otherSpan);

    final long from = startExpected;
    final long to = endExpected;

    final Response response = given().pathParam("token", uuidExpected)
        .queryParam("from", from).queryParam("to", to).when()
        .get("/v2/landscapes/{token}/structure");

    final Landscape result = response.getBody().as(Landscape.class);

    final List<Method> resultMethodList = result.nodes().get(0).applications().get(0).packages()
        .get(0).subPackages().get(0).classes().get(0).methods();

    Assertions.assertEquals(1, resultMethodList.size());
    Assertions.assertEquals(otherMethodName, resultMethodList.get(0).name());
  }

  @Test
  void testLoadTracesByTimeRange() {
    final long startEarly = 1701081837000L;
    final long endEarly = 1701081838000L;
    final long startExpected = 1701081840000L;
    final long endExpected = 1701081841000L;
    final long startLate = 1701081843000L;
    final long endLate = 1701081844000L;

    final UUID uuidExpected = UUID.randomUUID();

    final PersistenceSpan differentTokenSpan = new PersistenceSpan(
        UUID.randomUUID(), gitCommitChecksum, "123L", "", "1L", startEarly,
        endEarly, "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class.myMethod()",
        "847");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "123L", "", "1L", startEarly, endEarly, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847");

    final PersistenceSpan secondOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum,
        "789L", "", "3L", startLate, endLate, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847");

    final PersistenceSpan otherSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum, "456L",
        "", "2L", startExpected, endExpected, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName, "321");

    spanProcessor.accept(differentTokenSpan);
    spanProcessor.accept(firstOccurenceSpan);
    spanProcessor.accept(secondOccurenceSpan);
    spanProcessor.accept(otherSpan);

    final long from = startExpected;
    final long to = endExpected;

    final Response response = given().pathParam("token", uuidExpected)
        .queryParam("from", from).queryParam("to", to).when()
        .get("/v2/landscapes/{token}/dynamic");

    final Trace[] result = response.getBody().as(Trace[].class);

    // ATTENTION: For the moment, we only filter based on the starting point of
    // traces
    Assertions.assertEquals(2, result.length);
    Assertions.assertEquals(gitCommitChecksum, result[0].gitCommitChecksum());
  }

}
