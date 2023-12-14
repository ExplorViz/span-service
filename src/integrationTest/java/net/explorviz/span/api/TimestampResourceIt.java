package net.explorviz.span.api;

import static io.restassured.RestAssured.given;

import com.datastax.oss.quarkus.test.CassandraTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.explorviz.span.kafka.KafkaTestResource;
import net.explorviz.span.persistence.PersistenceSpan;
import net.explorviz.span.persistence.PersistenceSpanProcessor;
import net.explorviz.span.timestamp.Timestamp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(KafkaTestResource.class)
@QuarkusTestResource(CassandraTestResource.class)
public class TimestampResourceIt {

  @Inject
  PersistenceSpanProcessor spanProcessor;

  @Test
  void testLoadAllTimestampsForToken() {
    final long startEarly = 1702545564404L;
    final long endEarly = startEarly + 1000;
    final long startExpected = startEarly + 2000;
    final long endExpected = startExpected + 1000;
    final long startLate = startEarly + 10_000;
    final long endLate = startLate + 1000;

    final PersistenceSpan differentTokenSpan =
        new PersistenceSpan(UUID.fromString("8cd8a9a7-b840-4735-9ef0-2dbbfa01c039"), "123L", "",
            "1L", startEarly, endEarly, "nodeIp", "app-name", "java", 0,
            "net.explorviz.Class.myMethod()", "847");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstOccurenceSpan =
        new PersistenceSpan(PersistenceSpan.DEFAULT_UUID, "123L", "", "1L", startEarly, endEarly,
            "nodeIp", "app-name", "java", 0, "net.explorviz.Class." + duplicateMethodName, "847");

    final PersistenceSpan secondOccurenceSpan =
        new PersistenceSpan(PersistenceSpan.DEFAULT_UUID, "789L", "", "3L", startLate, endLate,
            "nodeIp", "app-name", "java", 0, "net.explorviz.Class." + duplicateMethodName, "847");

    final PersistenceSpan otherSpan =
        new PersistenceSpan(PersistenceSpan.DEFAULT_UUID, "456L", "0L", "", startExpected,
            endExpected, "nodeIp", "app-name", "java", 0, "net.explorviz.Class." + otherMethodName,
            "321");

    spanProcessor.accept(differentTokenSpan);
    spanProcessor.accept(firstOccurenceSpan);
    spanProcessor.accept(secondOccurenceSpan);
    spanProcessor.accept(otherSpan);

    final Response response = given().pathParam("token", PersistenceSpan.DEFAULT_UUID).when()
        .get("/v2/landscapes/{token}/timestamps");

    final List<Timestamp> resultList = response.getBody().as(new TypeRef<List<Timestamp>>() {
    });

    // Check that there are two timestamps in total for this token
    Assertions.assertEquals(2, resultList.size());

    // Check that there are the correct timestamp buckets with correct span count
    Optional<Timestamp> optionalTimestamp =
        resultList.stream().filter(timestamp -> timestamp.epochMilli() == 1702545560000L)
            .findFirst();

    if (optionalTimestamp.isEmpty()) {
      Assertions.fail(
          "Found no timestamp for time bucket 1702545560000L, but there should be one.");
    } else {
      Assertions.assertEquals(optionalTimestamp.get().spanCount(), 2);
    }

    optionalTimestamp =
        resultList.stream().filter(timestamp -> timestamp.epochMilli() == 1702545570000L)
            .findFirst();

    if (optionalTimestamp.isEmpty()) {
      Assertions.fail(
          "Found no timestamp for time bucket 1702545570000L, but there should be one.");
    } else {
      Assertions.assertEquals(optionalTimestamp.get().spanCount(), 1);
    }
  }


}


