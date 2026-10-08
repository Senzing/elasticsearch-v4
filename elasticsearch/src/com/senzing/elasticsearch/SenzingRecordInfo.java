package com.senzing.elasticsearch;

public class SenzingRecordInfo {
  // This is a container class for a JSON document for an input record.
  // It corresponds to what is initially loaded into the system,
  // or what is returned from SzEngine.getRecord().

  private String m_dataSource; // the data source for the record
  private String m_recordID; // the record ID of the record
  private String m_jsonData; // the JSON data of the record

  public SenzingRecordInfo(String dataSource, String recordID, String jsonData) {
    m_dataSource = dataSource;
    m_recordID = recordID;
    m_jsonData = jsonData;
  }

  public SenzingRecordInfo(String dataSource, String recordID) {
    m_dataSource = dataSource;
    m_recordID = recordID;
    m_jsonData = "";
  }

  public String getDataSource() { return m_dataSource; }
  public String getRecordID() { return m_recordID; }
  public String getJsonData() { return m_jsonData; }

  // helper function to get elastic search identifiers
  public static String getElasticSearchRecordIdentifier(SenzingRecordInfo recordInfo) {
    return ("{\"DATA_SOURCE\":\"" + recordInfo.m_dataSource + "\",\"RECORD_ID\":\"" + recordInfo.m_recordID + "\"}");
  }
}
