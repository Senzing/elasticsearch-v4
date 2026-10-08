package com.senzing.g2.elasticsearch;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_EXPORT_INCLUDE_ALL_ENTITIES;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.util.BinaryData;
import co.elastic.clients.util.ContentType;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class G2toElastic {
  public static void main(String[] args) {
    // define ElasticSearch index information

    String hostName = System.getenv("ELASTIC_HOSTNAME");
    String elasticSearchHostname = (hostName != null) ? hostName : "localhost"; // The hostname for the elasticsearch
                                                                                // instance

    String portNum = System.getenv("ELASTIC_PORT");
    Integer elasticSearchPortNumber = parsePort(portNum); // the exposed port for elasticsearch
    if (elasticSearchPortNumber == null) {
      System.out.println("The environment variable ELASTIC_PORT must be a port number from 1 to 65535, not: " + portNum);
      System.exit(1);
    }

    String indexName = System.getenv("ELASTIC_INDEX_NAME");
    // This value can be whatever you want, adhering to elasticsearch's index syntax
    String elasticSearchIndexName = (indexName != null && !indexName.isBlank()) ? indexName : "senzing-index";

    System.out.println("****Program started****");
    System.out.println("Initializing Senzing");

    // ****************************Creating Senzing environment********************
    // define Senzing connecting information
    String instanceName = "SenzingElasticSearch";
    boolean verboseLogging = false;
    String SENZING_ENGINE_CONFIGURATION_JSON = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
    if (SENZING_ENGINE_CONFIGURATION_JSON == null) {
      System.out.println(
          "The environment variable SENZING_ENGINE_CONFIGURATION_JSON must be set with a proper JSON configuration.");
      System.out.println(
          "Please see https://www.senzing.com/docs/tutorials/senzing_engine_config/");
      System.exit(1);
    }

    boolean success = false;
    SzEnvironment szEnvironment = null;
    try {
      // Connect to the Senzing engine
      System.out.println("Connecting to Senzing engine.");
      szEnvironment = SzCoreEnvironment.newBuilder()
          .instanceName(instanceName)
          .settings(SENZING_ENGINE_CONFIGURATION_JSON)
          .verboseLogging(verboseLogging)
          .build();
      SzEngine szEngine = szEnvironment.getEngine();

      // ****************************Creating elasticsearch objects********************
      System.out.println("Making elasticsearch clients");
      String elasticSearchUrl = "http://" + elasticSearchHostname + ":" + elasticSearchPortNumber;
      AtomicLong indexedCount = new AtomicLong();
      AtomicLong failedCount = new AtomicLong();

      try (ElasticsearchClient esClient = ElasticsearchClient.of(b -> b.host(elasticSearchUrl));
          BulkIngester<Void> ingester = BulkIngester.of(b -> b
              .client(esClient)
              .maxOperations(25) // This setting changes how many documents get sent at a times
              .flushInterval(250, TimeUnit.MILLISECONDS) // This setting changes how often the ingester gets flushed
              .listener(new BulkResultListener(indexedCount, failedCount)))) {

        Set<SzFlag> exportFlags = EnumSet.of(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
        exportFlags.addAll(SZ_EXPORT_INCLUDE_ALL_ENTITIES);
        long exportHandle = szEngine.exportJsonEntityReport(exportFlags);

        System.out.println("Indexing entities");
        try {
          String entity;
          while ((entity = szEngine.fetchNext(exportHandle)) != null) {
            G2EntityData entityData = new G2EntityData(entity);
            BinaryData data = BinaryData.of(entityData.getRecordData().getBytes(StandardCharsets.UTF_8),
                ContentType.APPLICATION_JSON);

            // This ingester does bulk indexes
            ingester.add(op -> op
                .index(idx -> idx
                    .index(elasticSearchIndexName)
                    .document(data)));
          }
        } finally {
          szEngine.closeExportReport(exportHandle);
        }
      }
      System.out.println("Finished indexing: " + indexedCount.get() + " entities indexed, "
          + failedCount.get() + " failed");
      success = (failedCount.get() == 0);

    } catch (SzException e) {
      System.out.println("Senzing error");
      System.out.println("Error Code = " + e.getErrorCode());
      System.out.println("Exception = " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // close the Senzing environment
      if (szEnvironment != null) {
        System.out.println("Closing Senzing environment.");
        szEnvironment.destroy();
      }
    }

    if (!success) {
      System.out.println("****Program failed****");
      System.exit(1);
    }
    System.out.println("****Program complete****");
    System.exit(0);
  }

  // Returns the Elasticsearch port, 9200 when unset, or null when the value isn't a valid port.
  static Integer parsePort(String portNum) {
    if (portNum == null || portNum.isBlank()) {
      return 9200;
    }
    try {
      int port = Integer.parseInt(portNum.trim());
      return (port >= 1 && port <= 65535) ? port : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // Counts documents that were and weren't indexed so failures don't pass silently.
  private static class BulkResultListener implements BulkListener<Void> {
    private final AtomicLong indexedCount;
    private final AtomicLong failedCount;

    BulkResultListener(AtomicLong indexedCount, AtomicLong failedCount) {
      this.indexedCount = indexedCount;
      this.failedCount = failedCount;
    }

    @Override
    public void beforeBulk(long executionId, BulkRequest request, List<Void> contexts) {
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Void> contexts, BulkResponse response) {
      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          failedCount.incrementAndGet();
          System.out.println("Failed to index document: " + item.error().reason());
        } else {
          indexedCount.incrementAndGet();
        }
      }
    }

    @Override
    public void afterBulk(long executionId, BulkRequest request, List<Void> contexts, Throwable failure) {
      failedCount.addAndGet(request.operations().size());
      System.out.println("Bulk request failed: " + failure);
    }
  }
}
