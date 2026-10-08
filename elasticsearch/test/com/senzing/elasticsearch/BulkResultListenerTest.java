package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkResultListenerTest {
  private final AtomicLong indexed = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final BulkResultListener listener = new BulkResultListener(indexed, failed);
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalErr;

  @BeforeEach
  void captureStderr() {
    originalErr = System.err;
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStderr() {
    System.setErr(originalErr);
  }

  @Test
  void countsSuccessfulItems() {
    listener.afterBulk(1, request(2), List.of(10L, 11L), response(success(), success()));
    assertEquals(2, indexed.get());
    assertEquals(0, failed.get());
    assertEquals("", stderr.toString(StandardCharsets.UTF_8));
  }

  @Test
  void countsAndReportsFailedItemsWithoutTheReason() {
    listener.afterBulk(1, request(3), List.of(10L, 11L, 12L), response(success(),
        failure("mapper_parsing_exception", "failed to parse field [NAME_FULL] with value [Robert Smith]"),
        success()));
    assertEquals(2, indexed.get());
    assertEquals(1, failed.get());
    String output = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("Failed to index entity 11: mapper_parsing_exception"), output);
    assertFalse(output.contains("Robert Smith"), output);
  }

  @Test
  void countsEveryOperationOfAFailedRequestAsFailed() {
    listener.afterBulk(1, request(4), List.of(1L, 2L, 3L, 4L), new ConnectException("Connection refused"));
    assertEquals(0, indexed.get());
    assertEquals(4, failed.get());
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Bulk request of 4 entities failed"));
  }

  @Test
  void reportsUnknownEntityWhenContextIsMissing() {
    listener.afterBulk(1, request(1), null, response(failure("version_conflict_engine_exception", "conflict")));
    assertEquals(1, failed.get());
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Failed to index entity unknown"));
  }

  private static BulkRequest request(int operations) {
    List<BulkOperation> ops = IntStream.range(0, operations)
        .mapToObj(i -> BulkOperation.of(o -> o.index(idx -> idx.index("senzing-index").document(Map.of("i", i)))))
        .toList();
    return BulkRequest.of(b -> b.operations(ops));
  }

  private static BulkResponse response(BulkResponseItem... items) {
    boolean errors = Arrays.stream(items).anyMatch(item -> item.error() != null);
    return BulkResponse.of(b -> b.errors(errors).took(1).items(List.of(items)));
  }

  private static BulkResponseItem success() {
    return BulkResponseItem.of(b -> b.operationType(OperationType.Index).index("senzing-index").status(201));
  }

  private static BulkResponseItem failure(String type, String reason) {
    return BulkResponseItem.of(b -> b.operationType(OperationType.Index).index("senzing-index").status(400)
        .error(ErrorCause.of(e -> e.type(type).reason(reason))));
  }
}
