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

  public static String getSimpleRawValue(JsonValue sourceValue) {
    ValueType valueType = sourceValue.getValueType();
    String result;
    switch (valueType) {
      case STRING:
        result = ((JsonString) sourceValue).getString();
        break;
      case ARRAY:
      case OBJECT:
      case NUMBER:
      case TRUE:
      case FALSE:
      case NULL:
      default:
        result = sourceValue.toString();
    }
    return result;
  }
}
