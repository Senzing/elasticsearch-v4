package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.senzing.sdk.SzException;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Runs the whole indexer against a fake Senzing environment and a fake elasticsearch _bulk endpoint.
class RunTest {
  private static final String CONFIG = "{\"PIPELINE\": {}}";

  private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private PrintStream originalOut;
  private PrintStream originalErr;
  private HttpServer server;
  // The (index, _id) of every document elasticsearch received
  private final List<String> indexedDocuments = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean rejectDocuments;

  @BeforeEach
  void setUp() throws IOException {
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      byte[] response = bulkResponse(body).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  // Answers a _bulk request: the action lines alternate with the documents.
  private String bulkResponse(String body) {
    JsonArrayBuilder items = Json.createArrayBuilder();
    String[] lines = body.split("\n");
    for (int i = 0; i < lines.length; i += 2) {
      JsonObject action;
      try (JsonReader reader = Json.createReader(new StringReader(lines[i]))) {
        action = reader.readObject().getJsonObject("index");
      }
      String index = action.getString("_index");
      String id = action.getString("_id");
      if (rejectDocuments) {
        items.add(Json.createObjectBuilder().add("index", Json.createObjectBuilder()
            .add("_index", index).add("_id", id).add("status", 400)
            .add("error", Json.createObjectBuilder().add("type", "mapper_parsing_exception")
                .add("reason", "failed to parse"))));
      } else {
        indexedDocuments.add(index + "/" + id);
        items.add(Json.createObjectBuilder().add("index", Json.createObjectBuilder()
            .add("_index", index).add("_id", id).add("status", 201).add("result", "created")));
      }
    }
    return Json.createObjectBuilder().add("took", 1).add("errors", rejectDocuments).add("items", items).build()
        .toString();
  }

  private Map<String, String> env(String elasticUrl) {
    Map<String, String> env = new HashMap<>();
    env.put("ELASTIC_URL", elasticUrl);
    env.put("SENZING_ENGINE_CONFIGURATION_JSON", CONFIG);
    return env;
  }

  private String elasticUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private static SenzingToElastic.SzEnvironmentFactory factory(List<String> entities, AtomicBoolean closed,
      AtomicBoolean destroyed, RuntimeException destroyFailure) {
    return config -> {
      assertEquals(CONFIG, config);
      return FakeSenzing.environment(FakeSenzing.engine(entities, closed), destroyed, destroyFailure);
    };
  }

  @Test
  void indexesEveryEntityByIdAndExitsZero() {
    AtomicBoolean closed = new AtomicBoolean();
    AtomicBoolean destroyed = new AtomicBoolean();
    Map<String, String> env = env(elasticUrl());
    env.put("ELASTIC_INDEX_NAME", "test-index");

    int exitCode = SenzingToElastic.run(env::get, factory(
        List.of(FakeSenzing.entity(1, "1001"), FakeSenzing.entity(2, "1002"), FakeSenzing.entity(5, "1005")),
        closed, destroyed, null));

    assertEquals(0, exitCode, stderr.toString(StandardCharsets.UTF_8));
    assertEquals(List.of("test-index/1", "test-index/2", "test-index/5"), indexedDocuments.stream().sorted().toList());
    assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Finished indexing: 3 entities indexed, 0 failed"));
    assertTrue(closed.get());
    assertTrue(destroyed.get());
  }

  @Test
  void usesTheDefaultIndexName() {
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, factory(List.of(FakeSenzing.entity(7, "1007")),
        new AtomicBoolean(), new AtomicBoolean(), null));
    assertEquals(0, exitCode);
    assertEquals(List.of("senzing-index/7"), indexedDocuments);
  }

  @Test
  void anEmptyExportSucceeds() {
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, factory(List.of(), new AtomicBoolean(),
        new AtomicBoolean(), null));
    assertEquals(0, exitCode);
    assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Finished indexing: 0 entities indexed, 0 failed"));
  }

  @Test
  void rejectedDocumentsExitOne() {
    rejectDocuments = true;
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, factory(
        List.of(FakeSenzing.entity(1, "1001"), FakeSenzing.entity(2, "1002")), new AtomicBoolean(),
        new AtomicBoolean(), null));
    assertEquals(1, exitCode);
    String errors = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(errors.contains("Failed to index entity 1: mapper_parsing_exception"), errors);
    assertTrue(errors.contains("****Program failed****"), errors);
  }

  @Test
  void unreachableElasticsearchExitsOne() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    AtomicBoolean destroyed = new AtomicBoolean();
    int exitCode = SenzingToElastic.run(env("http://127.0.0.1:" + closedPort)::get,
        factory(List.of(FakeSenzing.entity(1, "1001")), new AtomicBoolean(), destroyed, null));
    assertEquals(1, exitCode);
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Bulk request of 1 entities failed"));
    assertTrue(destroyed.get());
  }

  @Test
  void aSenzingErrorExitsOne() {
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, config -> {
      throw new SzException(7, "engine unavailable");
    });
    assertEquals(1, exitCode);
    String errors = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(errors.contains("Error Code = 7"), errors);
    assertTrue(indexedDocuments.isEmpty());
  }

  @Test
  void anUnexpectedErrorExitsOne() {
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, config -> {
      throw new IllegalStateException("unexpected");
    });
    assertEquals(1, exitCode);
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("IllegalStateException: unexpected"));
  }

  @Test
  void failingToCloseSenzingExitsOne() {
    int exitCode = SenzingToElastic.run(env(elasticUrl())::get, factory(List.of(FakeSenzing.entity(1, "1001")),
        new AtomicBoolean(), new AtomicBoolean(), new IllegalStateException("destroy failed")));
    assertEquals(1, exitCode);
    assertEquals(List.of("senzing-index/1"), indexedDocuments);
    assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Failed to close the Senzing environment"));
  }

  @Test
  void missingOrBlankEngineConfigurationExitsOneWithoutStartingSenzing() {
    AtomicReference<String> created = new AtomicReference<>();
    for (String config : new String[] { null, " " }) {
      Map<String, String> env = env(elasticUrl());
      env.put("SENZING_ENGINE_CONFIGURATION_JSON", config);
      int exitCode = SenzingToElastic.run(env::get, value -> {
        created.set(value);
        throw new AssertionError("Senzing should not be started");
      });
      assertEquals(1, exitCode);
    }
    assertEquals(null, created.get());
    assertTrue(stderr.toString(StandardCharsets.UTF_8)
        .contains("The environment variable SENZING_ENGINE_CONFIGURATION_JSON must be set"));
  }

  @Test
  void anInvalidElasticUrlExitsOneWithoutEchoingIt() {
    int exitCode = SenzingToElastic.run(env("ftp://user:secret@localhost")::get, value -> {
      throw new AssertionError("Senzing should not be started");
    });
    assertEquals(1, exitCode);
    String errors = stderr.toString(StandardCharsets.UTF_8);
    assertTrue(errors.contains("ELASTIC_URL must be"), errors);
    assertFalse(errors.contains("secret"), errors);
  }
}
