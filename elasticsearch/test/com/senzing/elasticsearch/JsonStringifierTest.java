package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonStringifierTest {
  @Test
  void convertsScalarsToStringsAtEveryDepth() {
    String result = JsonStringifier.stringifyJson("""
        {"NAME": "Ann", "AGE": 42, "SCORE": 1.5, "ACTIVE": false, "NOTE": null,
         "ADDRESS": {"ZIP": 89101, "LINES": ["1 Main St", 2]},
         "TAGS": [true, [3, "x"], {"N": 4}]}
        """);
    JsonObject expected = Utils.getDocumentFromJsonString("""
        {"NAME": "Ann", "AGE": "42", "SCORE": "1.5", "ACTIVE": "false", "NOTE": "null",
         "ADDRESS": {"ZIP": "89101", "LINES": ["1 Main St", "2"]},
         "TAGS": ["true", ["3", "x"], {"N": "4"}]}
        """);
    assertEquals(expected, Utils.getDocumentFromJsonString(result));
  }

  @Test
  void leavesAnEmptyObjectEmpty() {
    assertEquals(JsonValue.EMPTY_JSON_OBJECT, JsonStringifier.stringifyJson(Json.createObjectBuilder().build()));
  }
}
