package com.senzing.elasticsearch;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

// Converts every scalar value in a JSON document to a string, keeping the structure of
// objects and arrays, so elasticsearch maps every field the same way.
public class JsonStringifier {
  public static JsonObject stringifyJson(JsonObject sourceJson) {
    return stringifyValue(sourceJson).asJsonObject();
  }

  private static JsonValue stringifyValue(JsonValue sourceValue) {
    switch (sourceValue.getValueType()) {
      case ARRAY:
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        sourceValue.asJsonArray().forEach(entry -> arrayBuilder.add(stringifyValue(entry)));
        return arrayBuilder.build();
      case OBJECT:
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        sourceValue.asJsonObject().forEach((key, value) -> objectBuilder.add(key, stringifyValue(value)));
        return objectBuilder.build();
      default:
        return Json.createValue(Utils.getSimpleRawValue(sourceValue));
    }
  }
}
