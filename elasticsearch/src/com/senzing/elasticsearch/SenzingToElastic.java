package com.senzing.elasticsearch;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_EXPORT_INCLUDE_ALL_ENTITIES;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.util.BinaryData;
import co.elastic.clients.util.ContentType;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;
import com.senzing.sdk.SzException;
import com.senzing.sdk.SzFlag;
import com.senzing.sdk.core.SzCoreEnvironment;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

public class SenzingToElastic {
  static final String DEFAULT_ELASTIC_URL = "http://localhost:9200";
  static final String DEFAULT_INDEX_NAME = "senzing-index";

  public static void main(String[] args) {
    // The Elasticsearch client logs through SLF4J; only show its warnings and errors
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    System.exit(run());
  }

  static int run() {
    // define ElasticSearch index information

    // The full URL of the elasticsearch instance, including the scheme, port, and any credentials
    ElasticUrl elasticUrl;
    try {
      elasticUrl = ElasticUrl.parse(System.getenv("ELASTIC_URL"));
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 1;
    }

    // This value can be whatever you want, adhering to elasticsearch's index syntax
    String indexName = System.getenv("ELASTIC_INDEX_NAME");
    String elasticSearchIndexName = (indexName != null && !indexName.isBlank()) ? indexName : DEFAULT_INDEX_NAME;

    System.out.println("****Program started****");
    System.out.println("Initializing Senzing");

    // ****************************Creating Senzing environment********************
    // define Senzing connecting information
    String instanceName = "SenzingElasticSearch";
    boolean verboseLogging = false;
    String SENZING_ENGINE_CONFIGURATION_JSON = System.getenv("SENZING_ENGINE_CONFIGURATION_JSON");
    if (SENZING_ENGINE_CONFIGURATION_JSON == null) {
      System.err.println(
          "The environment variable SENZING_ENGINE_CONFIGURATION_JSON must be set with a proper JSON configuration.");
      System.err.println(
          "Please see https://www.senzing.com/docs/tutorials/senzing_engine_config/");
      return 1;
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
      AtomicLong indexedCount = new AtomicLong();
      AtomicLong failedCount = new AtomicLong();
      long exportedCount;

      try (ElasticsearchClient esClient = ElasticsearchClient.of(b -> {
        b.host(elasticUrl.host());
        if (elasticUrl.username() != null) {
          b.usernameAndPassword(elasticUrl.username(), elasticUrl.password());
        }
        return b;
      });
          BulkIngester<Long> ingester = BulkIngester.of(b -> b
              .client(esClient)
              .maxOperations(25) // This setting changes how many documents get sent at a times
              .flushInterval(250, TimeUnit.MILLISECONDS) // This setting changes how often the ingester gets flushed
              .listener(new BulkResultListener(indexedCount, failedCount)))) {

        System.out.println("Indexing entities");
        // This ingester does bulk indexes; the entity ID rides along so failures can be reported
        exportedCount = exportEntities(szEngine, (entityId, document) -> ingester.add(
            indexOperation(elasticSearchIndexName, entityId, document), entityId));
      }
      System.out.println("Finished indexing: " + indexedCount.get() + " entities indexed, "
          + failedCount.get() + " failed");
      success = (failedCount.get() == 0 && indexedCount.get() == exportedCount);
      if (indexedCount.get() + failedCount.get() != exportedCount) {
        System.err.println("Exported " + exportedCount + " entities but elasticsearch reported on "
            + (indexedCount.get() + failedCount.get()));
      }

    } catch (SzException e) {
      System.err.println("Senzing error");
      System.err.println("Error Code = " + e.getErrorCode());
      System.err.println("Exception = " + e.getMessage());
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      // close the Senzing environment
      if (szEnvironment != null) {
        System.out.println("Closing Senzing environment.");
        try {
          szEnvironment.destroy();
        } catch (Exception e) {
          System.err.println("Failed to close the Senzing environment");
          e.printStackTrace();
          success = false;
        }
      }
    }

    if (!success) {
      System.err.println("****Program failed****");
      return 1;
    }
    System.out.println("****Program complete****");
    return 0;
  }

  // Exports every entity from Senzing and hands each one's ID and indexable document to the sink.
  // Returns the number of entities exported.
  static long exportEntities(SzEngine szEngine, BiConsumer<Long, BinaryData> sink) throws SzException {
    Set<SzFlag> exportFlags = EnumSet.of(SZ_ENTITY_INCLUDE_RECORD_JSON_DATA);
    exportFlags.addAll(SZ_EXPORT_INCLUDE_ALL_ENTITIES);
    long exportHandle = szEngine.exportJsonEntityReport(exportFlags);

    long exportedCount = 0;
    try {
      String entity;
      while ((entity = szEngine.fetchNext(exportHandle)) != null) {
        SenzingEntityData entityData = new SenzingEntityData(entity);
        BinaryData document = BinaryData.of(entityData.getRecordData().getBytes(StandardCharsets.UTF_8),
            ContentType.APPLICATION_JSON);
        sink.accept(entityData.getEntityID(), document);
        exportedCount++;
      }
    } finally {
      szEngine.closeExportReport(exportHandle);
    }
    return exportedCount;
  }

  // Indexes the document under its Senzing entity ID, so re-running the indexer replaces each
  // entity's document instead of adding a duplicate.
  static BulkOperation indexOperation(String indexName, long entityId, BinaryData document) {
    return BulkOperation.of(op -> op
        .index(idx -> idx
            .index(indexName)
            .id(String.valueOf(entityId))
            .document(document)));
  }

  // The elasticsearch URL split into the host URL and any credentials it carried.
  record ElasticUrl(URI host, String username, String password) {
    static ElasticUrl parse(String value) {
      if (value == null || value.isBlank()) {
        value = DEFAULT_ELASTIC_URL;
      }

      // The value isn't echoed in errors because it may contain a password
      String usage = "The environment variable ELASTIC_URL must be an http:// or https:// URL with a host, "
          + "for example " + DEFAULT_ELASTIC_URL;
      URI uri;
      try {
        uri = new URI(value.trim());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(usage);
      }
      String scheme = uri.getScheme();
      if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
          || uri.getHost() == null) {
        throw new IllegalArgumentException(usage);
      }

      String username = null;
      String password = null;
      // Split before decoding, so a percent-encoded ':' stays part of the username or password
      String userInfo = uri.getRawUserInfo();
      if (userInfo != null) {
        int colon = userInfo.indexOf(':');
        username = decode((colon < 0) ? userInfo : userInfo.substring(0, colon));
        password = decode((colon < 0) ? "" : userInfo.substring(colon + 1));
      }

      try {
        URI host = new URI(scheme.toLowerCase(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
        return new ElasticUrl(host, username, password);
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(usage);
      }
    }

    // Percent-decodes a URL component; unlike form encoding, '+' is a literal plus sign
    private static String decode(String value) {
      return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
  }
}
