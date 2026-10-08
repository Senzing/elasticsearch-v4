package com.senzing.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class SenzingEntityDataTest {
  // A trimmed export document with the fields SenzingEntityData reads, plus some it should drop.
  private static final String ENTITY = """
      {
        "RESOLVED_ENTITY": {
          "ENTITY_ID": 42,
          "ENTITY_NAME": "Robert Smith",
          "RECORDS": [
            {
              "DATA_SOURCE": "CUSTOMERS",
              "RECORD_ID": "1001",
              "MATCH_KEY": "",
              "JSON_DATA": {"DATA_SOURCE": "CUSTOMERS", "RECORD_ID": "1001", "NAME_FULL": "Robert Smith"}
            },
            {
              "DATA_SOURCE": "WATCHLIST",
              "RECORD_ID": "W1",
              "MATCH_KEY": "+NAME+DOB",
              "JSON_DATA": {"DATA_SOURCE": "WATCHLIST", "RECORD_ID": "W1", "NAME_FULL": "Bob Smith"}
            }
          ]
        },
        "RELATED_ENTITIES": []
      }
      """;

  private static JsonObject parse(String json) {
    return Json.createReader(new StringReader(json)).readObject();
  }

  @Test
  void readsEntityId() {
    SenzingEntityData entityData = new SenzingEntityData(ENTITY);
    assertEquals(42L, entityData.getEntityID());
  }

  @Test
  void documentContainsOnlyRecordJsonDataAndRecordKeys() {
    JsonObject document = parse(new SenzingEntityData(ENTITY).getRecordData());
    assertEquals(2, document.size());

    JsonArray jsonData = document.getJsonArray("JSON_DATA");
    assertEquals(2, jsonData.size());
    assertEquals("Robert Smith", jsonData.getJsonObject(0).getString("NAME_FULL"));
    assertEquals("Bob Smith", jsonData.getJsonObject(1).getString("NAME_FULL"));

    JsonArray records = document.getJsonArray("RECORDS");
    assertEquals(2, records.size());
    assertEquals("CUSTOMERS", records.getJsonObject(0).getString("DATA_SOURCE"));
    assertEquals("1001", records.getJsonObject(0).getString("RECORD_ID"));
    assertEquals("WATCHLIST", records.getJsonObject(1).getString("DATA_SOURCE"));
    assertEquals("W1", records.getJsonObject(1).getString("RECORD_ID"));
    assertFalse(records.getJsonObject(0).containsKey("MATCH_KEY"));
  }

  @Test
  void entityWithNoRecordsHasEmptyArrays() {
    SenzingEntityData entityData = new SenzingEntityData("{\"RESOLVED_ENTITY\": {\"ENTITY_ID\": 7, \"RECORDS\": []}}");
    assertEquals(7L, entityData.getEntityID());

    JsonObject document = parse(entityData.getRecordData());
    assertEquals(0, document.getJsonArray("JSON_DATA").size());
    assertEquals(0, document.getJsonArray("RECORDS").size());
  }

  @Test
  void recordWithoutJsonDataIsStillListed() {
    String entity = """
        {"RESOLVED_ENTITY": {"ENTITY_ID": 3, "RECORDS": [
          {"DATA_SOURCE": "CUSTOMERS", "RECORD_ID": "1", "JSON_DATA": {"NAME_FULL": "Ann Lee"}},
          {"DATA_SOURCE": "WATCHLIST", "RECORD_ID": "W9"}
        ]}}
        """;
    JsonObject document = parse(new SenzingEntityData(entity).getRecordData());
    assertEquals(1, document.getJsonArray("JSON_DATA").size());
    assertEquals("Ann Lee", document.getJsonArray("JSON_DATA").getJsonObject(0).getString("NAME_FULL"));
    assertEquals(2, document.getJsonArray("RECORDS").size());
    assertEquals("W9", document.getJsonArray("RECORDS").getJsonObject(1).getString("RECORD_ID"));
  }

  @Test
  void recordValuesAreIndexedAsStrings() {
    String entity = """
        {"RESOLVED_ENTITY": {"ENTITY_ID": 1, "RECORDS": [{
          "DATA_SOURCE": "CUSTOMERS", "RECORD_ID": "1",
          "JSON_DATA": {"AMOUNT": 700, "ACTIVE": true, "NOTE": null}
        }]}}
        """;
    JsonObject jsonData = parse(new SenzingEntityData(entity).getRecordData())
        .getJsonArray("JSON_DATA").getJsonObject(0);
    assertEquals("700", jsonData.getString("AMOUNT"));
    assertEquals("true", jsonData.getString("ACTIVE"));
    assertEquals("null", jsonData.getString("NOTE"));
  }
}
