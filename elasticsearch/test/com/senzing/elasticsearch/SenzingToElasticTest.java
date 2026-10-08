package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.util.BinaryData;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class SenzingToElasticTest {
  private static final long EXPORT_HANDLE = 99L;

  @Test
  void elasticUrlDefaultsToLocalhostWhenUnset() {
    assertEquals("http://localhost:9200", SenzingToElastic.ElasticUrl.parse(null).host().toString());
    assertEquals("http://localhost:9200", SenzingToElastic.ElasticUrl.parse(" ").host().toString());
  }

  @Test
  void elasticUrlAcceptsHttpAndHttps() {
    SenzingToElastic.ElasticUrl url = SenzingToElastic.ElasticUrl.parse("http://senzing-elasticsearch:9200");
    assertEquals("http://senzing-elasticsearch:9200", url.host().toString());
    assertNull(url.username());
    assertNull(url.password());

    assertEquals("https://es.example.com", SenzingToElastic.ElasticUrl.parse("https://es.example.com").host().toString());
    assertEquals("https://es.example.com:443/prefix",
        SenzingToElastic.ElasticUrl.parse(" HTTPS://es.example.com:443/prefix ").host().toString());
  }

  @Test
  void elasticUrlSeparatesCredentials() {
    SenzingToElastic.ElasticUrl url = SenzingToElastic.ElasticUrl.parse("https://elastic:p%40ss:word@es.example.com:9200");
    assertEquals("https://es.example.com:9200", url.host().toString());
    assertEquals("elastic", url.username());
    assertEquals("p@ss:word", url.password());

    SenzingToElastic.ElasticUrl userOnly = SenzingToElastic.ElasticUrl.parse("http://reader@localhost:9200");
    assertEquals("reader", userOnly.username());
    assertEquals("", userOnly.password());
  }

  @Test
  void elasticUrlRejectsInvalidValuesWithoutEchoingThem() {
    for (String value : List.of("senzing-elasticsearch:9200", "ftp://localhost:9200", "http://", "not a url",
        "localhost")) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> SenzingToElastic.ElasticUrl.parse(value), value);
      assertTrue(e.getMessage().startsWith("The environment variable ELASTIC_URL must be"), value);
    }
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SenzingToElastic.ElasticUrl.parse("ftp://user:secret@localhost"));
    assertFalse(e.getMessage().contains("secret"));
  }

  @Test
  void exportEntitiesSendsEveryEntityAndClosesTheExport() throws SzException {
    AtomicBoolean closed = new AtomicBoolean();
    SzEngine engine = fakeEngine(List.of(entity(1, "1001"), entity(2, "1002")), closed);

    Map<Long, String> documents = new LinkedHashMap<>();
    long exported = SenzingToElastic.exportEntities(engine,
        (entityId, document) -> documents.put(entityId, toString(document)));

    assertEquals(2, exported);
    assertEquals(List.of(1L, 2L), new ArrayList<>(documents.keySet()));
    assertTrue(documents.get(1L).contains("\"RECORD_ID\":\"1001\""));
    assertTrue(closed.get());
  }

  @Test
  void exportEntitiesHandlesAnEmptyExport() throws SzException {
    AtomicBoolean closed = new AtomicBoolean();
    long exported = SenzingToElastic.exportEntities(fakeEngine(List.of(), closed),
        (entityId, document) -> {
          throw new AssertionError("no entities expected");
        });
    assertEquals(0, exported);
    assertTrue(closed.get());
  }

  @Test
  void exportEntitiesClosesTheExportWhenTheSinkFails() {
    AtomicBoolean closed = new AtomicBoolean();
    SzEngine engine = fakeEngine(List.of(entity(1, "1001")), closed);
    assertThrows(IllegalStateException.class, () -> SenzingToElastic.exportEntities(engine,
        (entityId, document) -> {
          throw new IllegalStateException("ingester closed");
        }));
    assertTrue(closed.get());
  }

  private static String entity(long entityId, String recordId) {
    return "{\"RESOLVED_ENTITY\": {\"ENTITY_ID\": " + entityId + ", \"RECORDS\": [{\"DATA_SOURCE\": \"CUSTOMERS\", "
        + "\"RECORD_ID\": \"" + recordId + "\", \"JSON_DATA\": {\"RECORD_ID\": \"" + recordId + "\"}}]}}";
  }

  private static String toString(BinaryData document) {
    try {
      ByteBuffer buffer = document.asByteBuffer();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // A stand-in SzEngine that serves the given entities from a single export handle.
  private static SzEngine fakeEngine(List<String> entities, AtomicBoolean closed) {
    Iterator<String> remaining = entities.iterator();
    return (SzEngine) Proxy.newProxyInstance(SzEngine.class.getClassLoader(), new Class<?>[] { SzEngine.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "exportJsonEntityReport" -> EXPORT_HANDLE;
          case "fetchNext" -> {
            assertEquals(EXPORT_HANDLE, args[0]);
            yield remaining.hasNext() ? remaining.next() : null;
          }
          case "closeExportReport" -> {
            assertEquals(EXPORT_HANDLE, args[0]);
            closed.set(true);
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
