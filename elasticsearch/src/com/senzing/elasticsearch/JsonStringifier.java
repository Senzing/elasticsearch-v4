package com.senzing.elasticsearch;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

public class JsonStringifier {
  public static String stringifyJson(String sourceJson) {
    StringReader sr = new StringReader(sourceJson);
    JsonReader jsonReader = Json.createReader(sr);
    JsonObject jsonObject = jsonReader.readObject();
    JsonObject alteredObject = stringifyJson(jsonObject);
    return alteredObject.toString();
  }

  public static JsonObject stringifyJson(JsonObject sourceJson) {
    JsonObjectBuilder objBuilder = Json.createObjectBuilder();
    sourceJson.entrySet().stream().forEach((entry) -> {
      String key = entry.getKey();
      JsonValue value = entry.getValue();
      buildStringifiedValue(key, value, objBuilder);
    });
    return objBuilder.build();
  }

  private static void buildStringifiedValue(String sourceKey, JsonValue sourceValue, JsonArrayBuilder outputBuilder) {
    ValueType valueType = sourceValue.getValueType();
    switch (valueType) {
      case ARRAY:
        JsonArray sourceArray = sourceValue.asJsonArray();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        sourceArray.stream().forEach((entry) -> {
          buildStringifiedValue(sourceKey, entry, arrayBuilder);
        });
        outputBuilder.add(arrayBuilder);
        break;
      case OBJECT:
        JsonObject sourceObject = sourceValue.asJsonObject();
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        sourceObject.entrySet().stream().forEach((entry) -> {
          String key = entry.getKey();
          JsonValue value = entry.getValue();
          buildStringifiedValue(key, value, objectBuilder);
        });
        outputBuilder.add(objectBuilder);
        break;
      default:
        outputBuilder.add(Utils.getSimpleRawValue(sourceValue));
    }
  }

  private static void buildStringifiedValue(String sourceKey, JsonValue sourceValue, JsonObjectBuilder outputBuilder) {
    ValueType valueType = sourceValue.getValueType();
    switch (valueType) {
      case ARRAY:
        JsonArray sourceArray = sourceValue.asJsonArray();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        sourceArray.stream().forEach((entry) -> {
          buildStringifiedValue(sourceKey, entry, arrayBuilder);
        });
        outputBuilder.add(sourceKey, arrayBuilder);
        break;
      case OBJECT:
        JsonObject sourceObject = sourceValue.asJsonObject();
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        sourceObject.entrySet().stream().forEach((entry) -> {
          String key = entry.getKey();
          JsonValue value = entry.getValue();
          buildStringifiedValue(key, value, objectBuilder);
        });
        outputBuilder.add(sourceKey, objectBuilder);
        break;
      default:
        outputBuilder.add(sourceKey, Utils.getSimpleRawValue(sourceValue));
    }
  }
}
