package net.explorviz.span.adapter.service.converter;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.HexFormat;
import net.explorviz.span.persistence.PersistenceSpan;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class SpanConverterImplTest {

  @Inject
  SpanConverterImpl spanConverter;

  private Span sampleSpan() {
    return Span.newBuilder()
        .setTraceId(ByteString.copyFrom(HexFormat.of().parseHex("50c246ad9c9883d1558df9f19b9ae7a6")))
        .setSpanId(ByteString.copyFrom(HexFormat.of().parseHex("7ef83c66eabd5fbb")))
        .setParentSpanId(ByteString.copyFrom(HexFormat.of().parseHex("7ef83c66efe42aaa")))
        .addAttributes(stringAttr("explorviz.token.id", PersistenceSpan.DEFAULT_UUID.toString()))
        .addAttributes(stringAttr("git_commit_checksum", "gitchecksum"))
        .addAttributes(stringAttr("host_address", "1.2.3.4"))
        .addAttributes(stringAttr("host", "testhostname"))
        .addAttributes(stringAttr("service.name", "testappname"))
        .addAttributes(stringAttr("service.instance.id", "42"))
        .addAttributes(stringAttr("telemetry.sdk.language", "java"))
        .addAttributes(stringAttr("code.function.name", "net.explorviz.test.Class.doSomething"))
        .setStartTimeUnixNano(1668069002431000000L)
        .setEndTimeUnixNano(1668072086000000000L)
        .build();
  }

  private KeyValue stringAttr(String key, String value) {
    return KeyValue.newBuilder()
        .setKey(key)
        .setValue(
            AnyValue.newBuilder()
                .setStringValue(value)
                .build()
        )
        .build();
  }

  private PersistenceSpan resultSpan() {
    return new PersistenceSpan(PersistenceSpan.DEFAULT_UUID,
        "gitchecksum",
        "7ef83c66eabd5fbb",
        "7ef83c66efe42aaa",
        "50c246ad9c9883d1558df9f19b9ae7a6", 1668069002431000000L, 1668072086000000000L,
        "1.2.3.4", "testhostname", "testappname", "java",
        "42",
        "net.explorviz.test.Class.doSomething",
        "-26423980179251174",
        "",
        "",
        "",
        "");
  }

  @Test
  public void testSpanToPersistenceSpanConversion() {
    final Span testSpan = this.sampleSpan();
    final PersistenceSpan expectedSpan = this.resultSpan();

    PersistenceSpan resultSpan = spanConverter.fromOpenTelemetrySpan(testSpan);

    Assertions.assertEquals(expectedSpan, resultSpan);
  }
}
