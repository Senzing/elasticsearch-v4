package com.senzing.g2.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class G2toElasticTest {
  @Test
  void parsePortDefaultsTo9200WhenUnset() {
    assertEquals(9200, G2toElastic.parsePort(null));
    assertEquals(9200, G2toElastic.parsePort(""));
    assertEquals(9200, G2toElastic.parsePort("  "));
  }

  @Test
  void parsePortAcceptsValidPorts() {
    assertEquals(9200, G2toElastic.parsePort("9200"));
    assertEquals(1, G2toElastic.parsePort("1"));
    assertEquals(65535, G2toElastic.parsePort(" 65535 "));
  }

  @Test
  void parsePortRejectsInvalidPorts() {
    assertNull(G2toElastic.parsePort("abc"));
    assertNull(G2toElastic.parsePort("92OO"));
    assertNull(G2toElastic.parsePort("0"));
    assertNull(G2toElastic.parsePort("-1"));
    assertNull(G2toElastic.parsePort("65536"));
  }
}
