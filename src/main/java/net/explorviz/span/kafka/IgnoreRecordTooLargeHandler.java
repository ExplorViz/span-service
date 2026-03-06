package net.explorviz.span.kafka;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.streams.errors.ErrorHandlerContext;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;

/**
 * {@link ProductionExceptionHandler} used in the application.properties file.
 * ({@link RecordTooLargeException}) lead to loss of records, but are not an unrecoverable error
 * that should shut down the application. Therefore, we catch the exception, discard the record, and
 * proceed.
 */
@RegisterForReflection
public class IgnoreRecordTooLargeHandler implements ProductionExceptionHandler {

  @Override
  public void configure(final Map<String, ?> configs) {
    // Nothing to do
  }

  @Override
  public ProductionExceptionHandlerResponse handle(final ErrorHandlerContext context,
      final ProducerRecord<byte[], byte[]> record, final Exception exception) {
    if (exception instanceof RecordTooLargeException) {
      return ProductionExceptionHandlerResponse.CONTINUE;
    } else {
      return ProductionExceptionHandlerResponse.FAIL;
    }
  }
}
