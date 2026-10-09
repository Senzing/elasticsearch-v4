package com.senzing.elasticsearch;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

public class Utils {
  // Returns a string's value without quotes, and any other value as its JSON text
  public static String getSimpleRawValue(JsonValue sourceValue) {
    if (sourceValue.getValueType() == ValueType.STRING) {
      return ((JsonString) sourceValue).getString();
    }
    return sourceValue.toString();
  }
}
