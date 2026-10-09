package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonStringifierTest {
  private static JsonObject parse(String json) {
    try (JsonReader reader = Json.createReader(new StringReader(json))) {
      return reader.readObject();
    }
  }

  @Test
  void convertsScalarsToStringsAtEveryDepth() {
    JsonObject result = JsonStringifier.stringifyJson(parse("""
        {"NAME": "Ann", "AGE": 42, "SCORE": 1.5, "ACTIVE": false, "NOTE": null,
         "ADDRESS": {"ZIP": 89101, "LINES": ["1 Main St", 2]},
         "TAGS": [true, [3, "x"], {"N": 4}]}
        """));
    JsonObject expected = parse("""
        {"NAME": "Ann", "AGE": "42", "SCORE": "1.5", "ACTIVE": "false", "NOTE": "null",
         "ADDRESS": {"ZIP": "89101", "LINES": ["1 Main St", "2"]},
         "TAGS": ["true", ["3", "x"], {"N": "4"}]}
        """);
    assertEquals(expected, result);
  }

  @Test
  void leavesAnEmptyObjectEmpty() {
    assertEquals(JsonValue.EMPTY_JSON_OBJECT, JsonStringifier.stringifyJson(Json.createObjectBuilder().build()));
  }
}
