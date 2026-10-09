package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.senzing.sdk.SzEngine;
import com.senzing.sdk.SzEnvironment;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Stand-ins for the Senzing SDK, so the indexer can be tested without a Senzing installation.
final class FakeSenzing {
  static final long EXPORT_HANDLE = 99L;

  private FakeSenzing() {
  }

  // An export document for an entity with a single CUSTOMERS record.
  static String entity(long entityId, String recordId) {
    return "{\"RESOLVED_ENTITY\": {\"ENTITY_ID\": " + entityId + ", \"RECORDS\": [{\"DATA_SOURCE\": \"CUSTOMERS\", "
        + "\"RECORD_ID\": \"" + recordId + "\", \"JSON_DATA\": {\"RECORD_ID\": \"" + recordId + "\"}}]}}";
  }

  // An SzEngine that serves the given entities from a single export handle.
  static SzEngine engine(List<String> entities, AtomicBoolean closed) {
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

  // An SzEnvironment around the given engine. destroyFailure, when not null, is thrown by destroy().
  static SzEnvironment environment(SzEngine engine, AtomicBoolean destroyed, RuntimeException destroyFailure) {
    return (SzEnvironment) Proxy.newProxyInstance(SzEnvironment.class.getClassLoader(),
        new Class<?>[] { SzEnvironment.class },
        (proxy, method, args) -> switch (method.getName()) {
          case "getEngine" -> engine;
          case "destroy" -> {
            destroyed.set(true);
            if (destroyFailure != null) {
              throw destroyFailure;
            }
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
