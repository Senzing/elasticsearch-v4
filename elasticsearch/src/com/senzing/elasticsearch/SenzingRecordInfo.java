package com.senzing.elasticsearch;

public class SenzingRecordInfo {
  // This is a container class for the key of an input record: its data source and record ID.

  private String m_dataSource; // the data source for the record
  private String m_recordID; // the record ID of the record

  public SenzingRecordInfo(String dataSource, String recordID) {
    m_dataSource = dataSource;
    m_recordID = recordID;
  }

  public String getDataSource() { return m_dataSource; }
  public String getRecordID() { return m_recordID; }
}
