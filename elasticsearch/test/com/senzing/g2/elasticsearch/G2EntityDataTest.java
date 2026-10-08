package com.senzing.g2.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class G2EntityDataTest {
  // A trimmed export document with the fields G2EntityData reads, plus some it should drop.
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
    G2EntityData entityData = new G2EntityData(ENTITY);
    assertEquals(42L, entityData.getEntityID());
    assertEquals("{\"ENTITY_ID\":\"42\"}", entityData.getElasticSearchEntityIdentifier());
  }

  @Test
  void documentContainsOnlyRecordJsonDataAndRecordKeys() {
    JsonObject document = parse(new G2EntityData(ENTITY).getRecordData());
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
}
