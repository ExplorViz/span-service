package net.explorviz.span.api;

import static io.restassured.RestAssured.given;

import com.datastax.oss.quarkus.test.CassandraTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  final String gitCommitChecksum = "testGitCommitChecksum";

  @Test
  void testLoadAllTimestampsForToken() {
    final long startEarly = 1702545564404L;
    final long endEarly = startEarly + 1000;
    final long startExpected = startEarly + 2000;
    final long endExpected = startExpected + 1000;
    final long startLate = startEarly + 10_000;
    final long endLate = startLate + 1000;

    final UUID uuidExpected = UUID.randomUUID();

    final PersistenceSpan differentTokenSpan = new PersistenceSpan(UUID.randomUUID(), gitCommitChecksum, "123L", "",
        "1L", startEarly, endEarly, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class.myMethod()", "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum, "123L", "", "1L",
        startEarly, endEarly,
        "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan secondOccurenceSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum, "789L", "", "3L",
        startLate, endLate,
        "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class." + duplicateMethodName, "847",
        "iamapod", "iamanode", "iamanamespace", "iamadeployment");

    final PersistenceSpan otherSpan = new PersistenceSpan(uuidExpected, gitCommitChecksum, "456L", "0L", "",
        startExpected,
        endExpected, "nodeIp", "host-name", "app-name", "java", 0, "net.explorviz.Class." + otherMethodName,
        "321", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(differentTokenSpan);
    spanProcessor.accept(firstOccurenceSpan);
    spanProcessor.accept(secondOccurenceSpan);
    spanProcessor.accept(otherSpan);

    final Response response = given().pathParam("token", uuidExpected).when()
        .get("/v2/landscapes/{token}/timestamps");

    final List<Timestamp> resultList = response.getBody().as(new TypeRef<List<Timestamp>>() {
    });

    // Check that there are two timestamps in total for this token
    Assertions.assertEquals(2, resultList.size());

    // Check that there are the correct timestamp buckets with correct span count
    // Check that there are the correct timestamp buckets with correct span count
    Map<Long, Integer> expectedValuesMap = new HashMap<Long, Integer>() {
      {
        put(1702545560000L, 2);
        put(1702545570000L, 1);
      }
    };

    for (final Map.Entry<Long, Integer> entry : expectedValuesMap.entrySet()) {
      long key = entry.getKey().longValue();
      int value = entry.getValue().intValue();

      Optional<Timestamp> optionalTimestamp = resultList.stream().filter(timestamp -> timestamp.epochMilli() == key)
          .findFirst();

      if (optionalTimestamp.isEmpty()) {
        Assertions.fail(
            "Found no timestamp for time bucket " + key + ", but there should be one.");
      } else {
        Assertions.assertEquals(value, optionalTimestamp.get().spanCount());
      }
    }
  }

  @Test
  void testLoadNewerTimestampsForToken() {
    final long firstBucketStart = 1702545554404L;
    final long firstBucketEnd = firstBucketStart + 1000;
    final long secondBucketStart = 1702545564404L;
    final long secondBucketEnd = secondBucketStart + 1000;
    final long thirdBucketStart = secondBucketStart + 10_000;
    final long thirdBucketEnd = thirdBucketStart + 1000;

    final UUID uuidExpected = UUID.randomUUID();

    final PersistenceSpan firstSpanOfFirstBucket = new PersistenceSpan(uuidExpected, gitCommitChecksum, "0123L", "",
        "1L", firstBucketStart, firstBucketEnd, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class.myMethod()", "847", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstSpanOfSecondBuckec = new PersistenceSpan(uuidExpected, gitCommitChecksum, "123L", "",
        "1L", secondBucketStart,
        secondBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan firstSpanOfThirdBucket = new PersistenceSpan(uuidExpected, gitCommitChecksum, "789L", "",
        "3L", thirdBucketStart,
        thirdBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan secondSpanOfSecondBucket = new PersistenceSpan(uuidExpected, gitCommitChecksum, "456L", "0L",
        "", secondBucketStart,
        secondBucketEnd, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName,
        "321", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(firstSpanOfFirstBucket);
    spanProcessor.accept(firstSpanOfSecondBuckec);
    spanProcessor.accept(firstSpanOfThirdBucket);
    spanProcessor.accept(secondSpanOfSecondBucket);

    final Response response = given().pathParam("token", uuidExpected).queryParam("newest", firstBucketStart - 10000)
        .when()
        .get("/v2/landscapes/{token}/timestamps");

    final List<Timestamp> resultList = response.getBody().as(new TypeRef<List<Timestamp>>() {
    });

    // System.out.println("HIER DA " + Arrays.deepToString(resultList.toArray()));

    Assertions.assertEquals(3, resultList.size());

    // Check that there are the correct timestamp buckets with correct span count
    Map<Long, Integer> expectedValuesMap = new HashMap<Long, Integer>() {
      {
        put(1702545550000L, 1);
        put(1702545560000L, 2);
        put(1702545570000L, 1);
      }
    };

    testResultListAgainstExpectedValues(resultList, expectedValuesMap);
  }

  @Test
  void testLoadAllTimestampsForTokenAndCommit() {
    final long firstBucketStart = 1702545554404L;
    final long firstBucketEnd = firstBucketStart + 1000;
    final long secondBucketStart = 1702545564404L;
    final long secondBucketEnd = secondBucketStart + 1000;
    final long thirdBucketStart = secondBucketStart + 10_000;
    final long thirdBucketEnd = thirdBucketStart + 1000;

    final UUID uuidExpected = UUID.randomUUID();
    final String expectedCommit = "testCommit";

    final PersistenceSpan firstSpanOfFirstBucket = new PersistenceSpan(uuidExpected, "notTestCommit-1", "0123L", "",
        "1L", firstBucketStart, firstBucketEnd, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class.myMethod()", "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstSpanOfSecondBuckec = new PersistenceSpan(uuidExpected, expectedCommit, "123L", "", "1L",
        secondBucketStart,
        secondBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan firstSpanOfThirdBucket = new PersistenceSpan(uuidExpected, "notTestCommit-2", "789L", "",
        "3L", thirdBucketStart,
        thirdBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan secondSpanOfSecondBucket = new PersistenceSpan(uuidExpected, "notTestCommit-2", "456L", "0L",
        "", secondBucketStart,
        secondBucketEnd, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName,
        "321", "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(firstSpanOfFirstBucket);
    spanProcessor.accept(firstSpanOfSecondBuckec);
    spanProcessor.accept(firstSpanOfThirdBucket);
    spanProcessor.accept(secondSpanOfSecondBucket);

    final Response response = given().pathParam("token", uuidExpected).queryParam("commit", expectedCommit)
        .when()
        .get("/v2/landscapes/{token}/timestamps");

    final List<Timestamp> resultList = response.getBody().as(new TypeRef<List<Timestamp>>() {
    });

    Assertions.assertEquals(1, resultList.size());

    // Check that there are the correct timestamp buckets with correct span count
    Map<Long, Integer> expectedValuesMap = new HashMap<Long, Integer>() {
      {
        put(1702545560000L, 1);
      }
    };

    testResultListAgainstExpectedValues(resultList, expectedValuesMap);
  }

  @Test
  void testLoadNewerTimestampsForTokenAndCommit() {
    final long firstBucketStart = 1702545554404L;
    final long firstBucketEnd = firstBucketStart + 1000;
    final long secondBucketStart = 1702545564404L;
    final long secondBucketEnd = secondBucketStart + 1000;
    final long thirdBucketStart = secondBucketStart + 10_000;
    final long thirdBucketEnd = thirdBucketStart + 1000;

    final UUID uuidExpected = UUID.randomUUID();
    final String expectedCommit = "testCommit";

    final PersistenceSpan firstSpanOfFirstBucket = new PersistenceSpan(uuidExpected, expectedCommit, "0123L", "",
        "1L", firstBucketStart, firstBucketEnd, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class.myMethod()", "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final String duplicateMethodName = "myMethodName()";
    final String otherMethodName = "myOtherMethodName()";

    final PersistenceSpan firstSpanOfSecondBuckec = new PersistenceSpan(uuidExpected, expectedCommit, "123L", "", "1L",
        secondBucketStart,
        secondBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan firstSpanOfThirdBucket = new PersistenceSpan(uuidExpected, expectedCommit, "789L", "", "3L",
        thirdBucketStart,
        thirdBucketEnd,
        "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + duplicateMethodName, "847",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    final PersistenceSpan secondSpanOfSecondBucket = new PersistenceSpan(uuidExpected, expectedCommit, "456L", "0L", "",
        secondBucketStart + 1000,
        secondBucketEnd + 1000, "nodeIp", "host-name", "app-name", "java", 0,
        "net.explorviz.Class." + otherMethodName,
        "321",
        "iamnotapod", "iamnotanode", "iamnotanamespace", "iamnotadeployment");

    spanProcessor.accept(firstSpanOfFirstBucket);
    spanProcessor.accept(firstSpanOfSecondBuckec);
    spanProcessor.accept(firstSpanOfThirdBucket);
    spanProcessor.accept(secondSpanOfSecondBucket);

    final Response response = given().pathParam("token", uuidExpected)
        .queryParam("newest", secondBucketEnd - 10000)
        .queryParam("commit", expectedCommit)
        .when()
        .get("/v2/landscapes/{token}/timestamps");

    final List<Timestamp> resultList = response.getBody().as(new TypeRef<List<Timestamp>>() {
    });

    Assertions.assertEquals(2, resultList.size());

    // Check that there are the correct timestamp buckets with correct span count
    Map<Long, Integer> expectedValuesMap = new HashMap<Long, Integer>() {
      {
        put(1702545560000L, 2);
        put(1702545570000L, 1);
      }
    };

    testResultListAgainstExpectedValues(resultList, expectedValuesMap);
  }

  private void testResultListAgainstExpectedValues(final List<Timestamp> resultList,
      final Map<Long, Integer> expectedValuesMap) {
    for (Map.Entry<Long, Integer> entry : expectedValuesMap.entrySet()) {
      long key = entry.getKey();
      int value = entry.getValue();

      Optional<Timestamp> optionalTimestamp = resultList.stream().filter(timestamp -> timestamp.epochMilli() == key)
          .findFirst();

      if (optionalTimestamp.isEmpty()) {
        Assertions.fail(
            "Found no timestamp for time bucket " + key + ", but there should be one.");
      } else {
        Assertions.assertEquals(value, optionalTimestamp.get().spanCount());
      }
    }
  }

}
