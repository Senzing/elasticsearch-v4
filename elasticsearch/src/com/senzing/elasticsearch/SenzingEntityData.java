package com.senzing.elasticsearch;

import java.io.StringReader;
import java.util.List;
import java.util.LinkedList;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

public class SenzingEntityData {
  // This class represents a minimal data representation for a resolved entity in
  // the search system. It contains only the entity ID, and the JSON records that
  // are contained in that resolved entity. (We keep the data at a minimal here,
  // so that we don't get other data showing up in search results.)

  private String m_resolvedEntityJsonData;
  private Long m_resolvedEntityID;

  public SenzingEntityData(String resolvedEntityDataString) {
    // This function parses a resolved entity document, and retrieves the necessary data.
    JsonObject jsonObject;
    try (JsonReader jsonReader = Json.createReader(new StringReader(resolvedEntityDataString))) {
      jsonObject = jsonReader.readObject();
    }
    JsonObject resEntObject = jsonObject.getJsonObject("RESOLVED_ENTITY");

    // get the entity ID
    JsonNumber resEntIDNum = resEntObject.getJsonNumber("ENTITY_ID");
    m_resolvedEntityID = resEntIDNum.longValue();

    // create a document writer for creating the document to be indexed
    JsonObjectBuilder outputBuilder = Json.createObjectBuilder();

    // get the json data from the individual records within the resolved entity.
    JsonArrayBuilder jsonDataArrayBuilder = Json.createArrayBuilder();
    JsonArray recordsArray = resEntObject.getJsonArray("RECORDS");
    int numRecordsInArray = recordsArray.size();
    List<SenzingRecordInfo> recordInfoList = new LinkedList<SenzingRecordInfo>();
    for (int i = 0; i < numRecordsInArray; i++) {
      JsonObject recordObject = recordsArray.getJsonObject(i);
      // A record without JSON_DATA is still listed in RECORDS, it just has no data to search
      JsonObject recordJsonDataObject = recordObject.getJsonObject("JSON_DATA");
      if (recordJsonDataObject != null) {
        jsonDataArrayBuilder.add(recordJsonDataObject);
      }

      // Senzing always includes these, but a missing one is left out rather than failing the export
      recordInfoList.add(new SenzingRecordInfo(recordObject.getString("DATA_SOURCE", null),
          recordObject.getString("RECORD_ID", null)));
    }
    outputBuilder.add("JSON_DATA", jsonDataArrayBuilder);
    JsonArrayBuilder recordArrayBuilder = Json.createArrayBuilder();
    for (SenzingRecordInfo recordInfo : recordInfoList) {
      JsonObjectBuilder recordInfoBuilder = Json.createObjectBuilder();
      if (recordInfo.getDataSource() != null) {
        recordInfoBuilder.add("DATA_SOURCE", recordInfo.getDataSource());
      }
      if (recordInfo.getRecordID() != null) {
        recordInfoBuilder.add("RECORD_ID", recordInfo.getRecordID());
      }
      recordArrayBuilder.add(recordInfoBuilder);
    }
    outputBuilder.add("RECORDS", recordArrayBuilder);

    // store the indexable document
    m_resolvedEntityJsonData = JsonStringifier.stringifyJson(outputBuilder.build()).toString();
  }

  public String getRecordData() {
    return m_resolvedEntityJsonData;
  }

  public Long getEntityID() {
    return m_resolvedEntityID;
  }
}
