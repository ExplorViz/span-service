package net.explorviz.span.persistence;

import java.util.UUID;
import net.explorviz.avro.Span;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SpanConverterTest {

  SpanConverter converter;

  @BeforeEach
  public void setUp() {
    this.converter = new SpanConverter();
  }

  private Span sampleSpan() {
    return Span.newBuilder()
        .setLandscapeToken(PersistenceSpan.DEFAULT_UUID.toString())
        .setGitCommitChecksum("gitchecksum")
        .setTraceId("50c246ad9c9883d1558df9f19b9ae7a6")
        .setSpanId("7ef83c66eabd5fbb")
        .setParentSpanId("7ef83c66efe42aaa")
        .setHostIpAddress("1.2.3.4")
        .setHostname("testhostname")
        .setAppName("testappname")
        .setAppInstanceId("42")
        .setAppLanguage("java")
        .setFullyQualifiedOperationName("asd.bfd")
        .setHashCode("-909819732013219679")
        .setStartTimeEpochMilli(1668069002431000000L)
        .setEndTimeEpochMilli(1668072086000000000L)
        .build();
  }

  private PersistenceSpan convertSpanToPersistenceSpan(final Span span) {
    return new PersistenceSpan(UUID.fromString(span.getLandscapeToken()),
        span.getGitCommitChecksum(),
        span.getSpanId(),
        span.getParentSpanId(),
        span.getTraceId(), span.getStartTimeEpochMilli(), span.getEndTimeEpochMilli(),
        span.getHostIpAddress(), span.getHostname(), span.getAppName(), span.getAppLanguage(),
        span.getAppInstanceId(),
        span.getFullyQualifiedOperationName(),
        span.getHashCode(),
        span.getK8sPodName(),
        span.getK8sNodeName(),
        span.getK8sNamespace(),
        span.getK8sDeploymentName());
  }

  @Test
  public void testSpanToPersistenceSpanConversion() {
    final Span testSpan = this.sampleSpan();
    final PersistenceSpan expectedSpan = this.convertSpanToPersistenceSpan(testSpan);

    PersistenceSpan resultSpan = this.converter.apply(testSpan);

    Assertions.assertEquals(expectedSpan, resultSpan);
  }
}
