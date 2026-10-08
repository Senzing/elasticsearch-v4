package com.senzing.elasticsearch;

import static com.senzing.sdk.SzFlag.SZ_ENTITY_INCLUDE_RECORD_JSON_DATA;
import static com.senzing.sdk.SzFlag.SZ_EXPORT_INCLUDE_ALL_ENTITIES;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
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
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;

public class SenzingToElastic {
  static final String DEFAULT_ELASTIC_URL = "http://localhost:9200";
  static final String DEFAULT_INDEX_NAME = "senzing-index";

  public static void main(String[] args) {
    // The Elasticsearch client logs through SLF4J; only show its warnings and errors
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
    System.exit(run(System::getenv, SenzingToElastic::createSzEnvironment));
  }

  // Creates the Senzing environment from the engine configuration JSON
  interface SzEnvironmentFactory {
    SzEnvironment create(String engineConfigJson) throws SzException;
  }

  static SzEnvironment createSzEnvironment(String engineConfigJson) {
    // define Senzing connecting information
    String instanceName = "SenzingElasticSearch";
    boolean verboseLogging = false;
    return SzCoreEnvironment.newBuilder()
        .instanceName(instanceName)
        .settings(engineConfigJson)
        .verboseLogging(verboseLogging)
        .build();
  }

  // Reads its settings through env, so tests can supply their own
  static int run(Function<String, String> env, SzEnvironmentFactory szEnvironmentFactory) {
    // define ElasticSearch index information

    // The full URL of the elasticsearch instance, including the scheme, port, and any credentials
    ElasticUrl elasticUrl;
    try {
      elasticUrl = ElasticUrl.parse(env.apply("ELASTIC_URL"));
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      return 1;
    }

    // This value can be whatever you want, adhering to elasticsearch's index syntax
    String indexName = env.apply("ELASTIC_INDEX_NAME");
    String elasticSearchIndexName = (indexName != null && !indexName.isBlank()) ? indexName : DEFAULT_INDEX_NAME;

    System.out.println("****Program started****");
    System.out.println("Initializing Senzing");

    // ****************************Creating Senzing environment********************
    String engineConfigJson = env.apply("SENZING_ENGINE_CONFIGURATION_JSON");
    if (engineConfigJson == null || engineConfigJson.isBlank()) {
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
      szEnvironment = szEnvironmentFactory.create(engineConfigJson);
      SzEngine szEngine = szEnvironment.getEngine();

      // ****************************Creating elasticsearch objects********************
      System.out.println("Making elasticsearch clients");
      AtomicLong indexedCount = new AtomicLong();
      AtomicLong failedCount = new AtomicLong();
      long exportedCount;

      try (ElasticsearchClient esClient = createClient(elasticUrl);
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

  // Builds the client from the URL's parts rather than from a java.net.URI, because the client
  // reads the host with URI.getHost(), which is null for hostnames such as "senzing_es".
  static ElasticsearchClient createClient(ElasticUrl elasticUrl) throws NoSuchAlgorithmException {
    // HttpHost takes an IPv6 address without the brackets the URL needs
    String hostName = elasticUrl.hostName().replaceAll("^\\[(.*)\\]$", "$1");
    Rest5ClientBuilder restClientBuilder = Rest5Client.builder(
        new HttpHost(elasticUrl.scheme(), hostName, elasticUrl.port()));
    if (!elasticUrl.path().isEmpty() && !elasticUrl.path().equals("/")) {
      restClientBuilder.setPathPrefix(elasticUrl.path());
    }
    if (elasticUrl.username() != null) {
      String credentials = elasticUrl.username() + ":" + elasticUrl.password();
      restClientBuilder.setDefaultHeaders(new Header[] { new BasicHeader("Authorization",
          "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8))) });
    }
    if (elasticUrl.scheme().equals("https")) {
      // The JVM's default context honors -Djavax.net.ssl.trustStore
      restClientBuilder.setSSLContext(SSLContext.getDefault());
    }
    return new ElasticsearchClient(new Rest5ClientTransport(restClientBuilder.build(), new JacksonJsonpMapper()));
  }

  // The parts of the elasticsearch URL: the scheme, host, port (-1 for the scheme's default),
  // path, and any credentials.
  record ElasticUrl(String scheme, String hostName, int port, String path, String username, String password) {
    // The URL without credentials, safe to print
    String hostUrl() {
      return scheme + "://" + hostName + ((port < 0) ? "" : ":" + port) + path;
    }

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
          || uri.getRawAuthority() == null) {
        throw new IllegalArgumentException(usage);
      }

      String hostName = uri.getHost();
      int port = uri.getPort();
      String userInfo = uri.getRawUserInfo();
      if (hostName == null) {
        // Java's URI parser rejects some valid hostnames, such as Docker container names with
        // underscores, so split the authority into user info, host, and port here instead
        String authority = uri.getRawAuthority();
        int at = authority.lastIndexOf('@');
        userInfo = (at < 0) ? null : authority.substring(0, at);
        String hostAndPort = authority.substring(at + 1);
        int colon = hostAndPort.lastIndexOf(':');
        hostName = (colon < 0) ? hostAndPort : hostAndPort.substring(0, colon);
        if (colon >= 0) {
          try {
            port = Integer.parseInt(hostAndPort.substring(colon + 1));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException(usage);
          }
        }
        if (!hostName.matches("[A-Za-z0-9._-]+") || port < -1 || port > 65535) {
          throw new IllegalArgumentException(usage);
        }
      }

      String username = null;
      String password = null;
      // Split before decoding, so a percent-encoded ':' stays part of the username or password
      if (userInfo != null) {
        int colon = userInfo.indexOf(':');
        username = decode((colon < 0) ? userInfo : userInfo.substring(0, colon));
        password = decode((colon < 0) ? "" : userInfo.substring(colon + 1));
      }

      // The query and fragment aren't used
      String path = (uri.getRawPath() == null) ? "" : uri.getRawPath();
      return new ElasticUrl(scheme.toLowerCase(), hostName, port, path, username, password);
    }

    // Percent-decodes a URL component; unlike form encoding, '+' is a literal plus sign
    private static String decode(String value) {
      return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
  }
}
