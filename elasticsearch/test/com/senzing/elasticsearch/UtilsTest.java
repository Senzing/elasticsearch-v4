package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class UtilsTest {
  @Test
  void parsesAJsonObject() {
    JsonObject document = Utils.getDocumentFromJsonString("{\"DATA_SOURCE\": \"CUSTOMERS\", \"COUNT\": 3}");
    assertEquals("CUSTOMERS", document.getString("DATA_SOURCE"));
    assertEquals(3, document.getInt("COUNT"));
  }

  @Test
  void returnsStringsWithoutQuotes() {
    assertEquals("Ann", Utils.getSimpleRawValue(Json.createValue("Ann")));
  }

  @Test
  void returnsOtherValuesAsTheirJsonText() {
    assertEquals("42", Utils.getSimpleRawValue(Json.createValue(42)));
    assertEquals("true", Utils.getSimpleRawValue(JsonValue.TRUE));
    assertEquals("null", Utils.getSimpleRawValue(JsonValue.NULL));
    assertEquals("[1,\"a\"]", Utils.getSimpleRawValue(Json.createArrayBuilder().add(1).add("a").build()));
    assertEquals("{\"A\":1}", Utils.getSimpleRawValue(Json.createObjectBuilder().add("A", 1).build()));
  }
}
