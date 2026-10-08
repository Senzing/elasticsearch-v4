package com.senzing.elasticsearch;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

public class Utils {
  public static JsonObject getDocumentFromJsonString(String jsonString) {
    StringReader reader = new StringReader(jsonString);
    JsonReader jsonReader = Json.createReader(reader);
    JsonObject resultDoc = jsonReader.readObject();
    jsonReader.close();
    return resultDoc;
  }

  // Returns a string's value without quotes, and any other value as its JSON text
  public static String getSimpleRawValue(JsonValue sourceValue) {
    if (sourceValue.getValueType() == ValueType.STRING) {
      return ((JsonString) sourceValue).getString();
    }
    return sourceValue.toString();
  }
}
