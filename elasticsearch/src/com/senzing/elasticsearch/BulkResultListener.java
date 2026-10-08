package com.senzing.elasticsearch;

import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// Counts documents that were and weren't indexed so failures don't pass silently.
// The context of each operation is the Senzing entity ID of the document.
class BulkResultListener implements BulkListener<Long> {
  private final AtomicLong indexedCount;
  private final AtomicLong failedCount;

  BulkResultListener(AtomicLong indexedCount, AtomicLong failedCount) {
    this.indexedCount = indexedCount;
    this.failedCount = failedCount;
  }

  @Override
  public void beforeBulk(long executionId, BulkRequest request, List<Long> contexts) {
  }

  @Override
  public void afterBulk(long executionId, BulkRequest request, List<Long> contexts, BulkResponse response) {
    List<BulkResponseItem> items = response.items();
    for (int i = 0; i < items.size(); i++) {
      BulkResponseItem item = items.get(i);
      if (item.error() != null) {
        failedCount.incrementAndGet();
        // Only the error type is printed; the reason can echo parts of the document
        System.err.println("Failed to index entity " + entityId(contexts, i) + ": " + item.error().type());
      } else {
        indexedCount.incrementAndGet();
      }
    }
  }

  @Override
  public void afterBulk(long executionId, BulkRequest request, List<Long> contexts, Throwable failure) {
    failedCount.addAndGet(request.operations().size());
    System.err.println("Bulk request of " + request.operations().size() + " entities failed: " + failure);
  }

  private static String entityId(List<Long> contexts, int index) {
    return (contexts != null && index < contexts.size()) ? String.valueOf(contexts.get(index)) : "unknown";
  }
}
