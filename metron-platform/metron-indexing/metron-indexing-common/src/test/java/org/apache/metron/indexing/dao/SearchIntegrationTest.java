
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.metron.indexing.dao;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.indexing.dao.search.*;
import org.apache.metron.indexing.dao.update.Document;
import org.apache.metron.integration.InMemoryComponent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public abstract class SearchIntegrationTest {
  /**
   * [
   * {"source:type": "bro", "ip_src_addr":"192.168.1.1", "ip_src_port": 8010, "long_field": 10000, "timestamp":1, "latitude": 48.5839, "score": 10.0, "is_alert":true, "location_point": "48.5839,7.7455", "method": "bro data 1", "ttl": "data 1", "guid":"bro_1"},
   * {"source:type": "bro", "ip_src_addr":"192.168.1.2", "ip_src_port": 8009, "long_field": 20000, "timestamp":2, "latitude": 48.0001, "score": 50.0, "is_alert":false, "location_point": "48.5839,7.7455", "method": "bro data 2", "ttl": "data 2", "guid":"bro_2"},
   * {"source:type": "bro", "ip_src_addr":"192.168.1.3", "ip_src_port": 8008, "long_field": 10000, "timestamp":3, "latitude": 48.5839, "score": 20.0, "is_alert":true, "location_point": "50.0,7.7455", "method": "bro data 3", "ttl": "data 3", "guid":"bro_3"},
   * {"source:type": "bro", "ip_src_addr":"192.168.1.4", "ip_src_port": 8007, "long_field": 10000, "timestamp":4, "latitude": 48.5839, "score": 10.0, "is_alert":true, "location_point": "48.5839,7.7455", "method": "bro data 4", "ttl": "data 4", "guid":"bro_4"},
   * {"source:type": "bro", "ip_src_addr":"192.168.1.5", "ip_src_port": 8006, "long_field": 10000, "timestamp":5, "latitude": 48.5839, "score": 98.0, "is_alert":true, "location_point": "48.5839,7.7455", "method": "bro data 5", "ttl": "data 5", "guid":"bro_5"}
   * ]
   */
  @Multiline
  public static String broData;

  /**
   * [
   * {"source:type": "snort", "ip_src_addr":"192.168.1.6", "ip_src_port": 8005, "long_field": 10000, "timestamp":6, "latitude": 48.5839, "score": 50.0, "is_alert":false, "location_point": "50.0,7.7455", "sig_generator": "sig_generator 1", "ttl": 1, "guid":"snort_1", "threat:triage:score":10.0},
   * {"source:type": "snort", "ip_src_addr":"192.168.1.1", "ip_src_port": 8004, "long_field": 10000, "timestamp":7, "latitude": 48.5839, "score": 10.0, "is_alert":true, "location_point": "48.5839,7.7455", "sig_generator": "sig_generator 2", "ttl": 2, "guid":"snort_2", "threat:triage:score":20.0},
   * {"source:type": "snort", "ip_src_addr":"192.168.1.7", "ip_src_port": 8003, "long_field": 10000, "timestamp":8, "latitude": 48.5839, "score": 20.0, "is_alert":false, "location_point": "48.5839,7.7455", "sig_generator": "sig_generator 3", "ttl": 3, "guid":"snort_3"},
   * {"source:type": "snort", "ip_src_addr":"192.168.1.1", "ip_src_port": 8002, "long_field": 20000, "timestamp":9, "latitude": 48.0001, "score": 50.0, "is_alert":true, "location_point": "48.5839,7.7455", "sig_generator": "sig_generator 4", "ttl": 4, "guid":"snort_4"},
   * {"source:type": "snort", "ip_src_addr":"192.168.1.8", "ip_src_port": 8001, "long_field": 10000, "timestamp":10, "latitude": 48.5839, "score": 10.0, "is_alert":false, "location_point": "48.5839,7.7455", "sig_generator": "sig_generator 5", "ttl": 5, "guid":"snort_5"}
   * ]
   */
  @Multiline
  public static String snortData;

  /**
   * {
   * "indices": ["bro", "snort", "some_collection"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String allQuery;

  /**
   * {
   * "guid": "bro_3",
   * "sensorType": "bro"
   * }
   */
  @Multiline
  public static String findOneGuidQuery;

  /**
   * [
   * {
   * "guid": "bro_1",
   * "sensorType": "bro"
   * },
   * {
   * "guid": "snort_2",
   * "sensorType": "snort"
   * }
   * ]
   */
  @Multiline
  public static String getAllLatestQuery;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "ip_src_addr:192.168.1.1",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String filterQuery;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "ip_src_port",
   *     "sortOrder": "asc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String sortQuery;

  /**
   * {
   *  "indices": [
   *    "snort",
   *    "bro"
   *  ],
   * "query": "*",
   * "from": 0,
   * "size": 25,
   * "sort": [
   *    {
   *      "field": "threat:triage:score",
   *      "sortOrder": "asc"
   *    }
   *  ]
   * }
   */
  @Multiline
  public static String sortAscendingWithMissingFields;

  /**
   * {
   *  "indices": [
   *    "snort",
   *    "bro"
   *  ],
   * "query": "*",
   * "from": 0,
   * "size": 25,
   * "sort": [
   *    {
   *      "field": "threat:triage:score",
   *      "sortOrder": "desc"
   *    }
   *  ]
   * }
   */
  @Multiline
  public static String sortDescendingWithMissingFields;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 4,
   * "size": 3,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String paginationQuery;

  /**
   * {
   * "indices": ["bro"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String indexQuery;

  /**
   * {
   * "facetFields": ["source:type", "ip_src_addr", "ip_src_port", "long_field", "timestamp", "latitude", "score", "is_alert"],
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String facetQueryRaw;

  /**
   * {
   * "facetFields": ["location_point"],
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String badFacetQuery;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String disabledFacetQuery;

  /**
   * {
   * "facetFields": ["sig_generator"],
   * "indices": ["bro", "snort"],
   * "query": "*:*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String missingTypeFacetQuery;

  /**
   * {
   * "facetFields": ["ttl"],
   * "indices": ["bro", "snort"],
   * "query": "*:*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String differentTypeFacetQuery;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 101,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String exceededMaxResultsQuery;

  /**
   * {
   * "fields": ["ip_src_addr"],
   * "indices": ["bro", "snort"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String fieldsQuery;

  /**
   * {
   * "fields": ["ip_src_addr"],
   * "indices": ["bro", "snort"],
   * "query": "ip_src_addr:192.168.1.9",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String noResultsFieldsQuery;

  /**
   * {
   * "fields": ["guid"],
   * "indices": ["bro"],
   * "query": "*",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "guid",
   *     "sortOrder": "asc"
   *   }
   * ]
   * }
   * }
   */
  @Multiline
  public static String sortByGuidQuery;

  /**
   * {
   * "groups": [
   *   {
   *     "field":"is_alert"
   *   },
   *   {
   *     "field":"latitude"
   *   }
   * ],
   * "scoreField":"score",
   * "indices": ["bro", "snort"],
   * "query": "*"
   * }
   */
  @Multiline
  public static String groupByQuery;

  /**
   * {
   * "groups": [
   *   {
   *     "field":"is_alert",
   *     "order": {
   *       "groupOrderType": "count",
   *       "sortOrder": "ASC"
   *     }
   *   },
   *   {
   *     "field":"ip_src_addr",
   *     "order": {
   *       "groupOrderType": "term",
   *       "sortOrder": "DESC"
   *     }
   *   }
   * ],
   * "indices": ["bro", "snort"],
   * "query": "*"
   * }
   */
  @Multiline
  public static String sortedGroupByQuery;

  /**
   * {
   * "groups": [
   *   {
   *     "field":"location_point"
   *   }
   * ],
   * "indices": ["bro", "snort"],
   * "query": "*"
   * }
   */
  @Multiline
  public static String badGroupQuery;

  /**
   * {
   * "groups": [
   *   {
   *     "field":"ip_src_addr",
   *     "order": {
   *       "groupOrderType": "term",
   *       "sortOrder": "DESC"
   *     }
   *   }
   * ],
   * "indices": ["bro", "snort"],
   * "query": "*"
   * }
   */
  @Multiline
  public static String groupByIpQuery;

  /**
   * {
   * "indices": ["bro", "snort"],
   * "query": "ttl:\"data 1\"",
   * "from": 0,
   * "size": 10,
   * "sort": [
   *   {
   *     "field": "timestamp",
   *     "sortOrder": "desc"
   *   }
   * ]
   * }
   */
  @Multiline
  public static String differentTypeFilterQuery;

  protected static InMemoryComponent indexComponent;

  @Test
  public void all_query_returns_all_results() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(allQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    assertEquals(10, results.size());
    for(int i = 0;i < 5;++i) {
      assertEquals("snort", results.get(i).getSource().get(getSourceTypeField()));
      assertEquals(getIndexName("snort"), results.get(i).getIndex());
      assertEquals(10 - i + "", results.get(i).getSource().get("timestamp").toString());
    }
    for (int i = 5; i < 10; ++i) {
      assertEquals("bro", results.get(i).getSource().get(getSourceTypeField()));
      assertEquals(getIndexName("bro"), results.get(i).getIndex());
      assertEquals(10 - i + "", results.get(i).getSource().get("timestamp").toString());
    }
  }

  @Test
  public void find_one_guid() throws Exception {
    GetRequest request = JSONUtils.INSTANCE.load(findOneGuidQuery, GetRequest.class);
    Optional<Map<String, Object>> response = getIndexDao().getLatestResult(request);
    assertTrue(response.isPresent());
    Map<String, Object> doc = response.get();
    assertEquals("bro", doc.get(getSourceTypeField()));
    assertEquals("3", doc.get("timestamp").toString());
  }

  @Test
  public void get_all_latest_guid() throws Exception {
    List<GetRequest> request = JSONUtils.INSTANCE.load(getAllLatestQuery, new JSONUtils.ReferenceSupplier<List<GetRequest>>(){});
    Map<String, Document> docs = new HashMap<>();

    for(Document doc : getIndexDao().getAllLatest(request)) {
      docs.put(doc.getGuid(), doc);
    }
    assertEquals(2, docs.size());
    assertTrue(docs.keySet().contains("bro_1"));
    assertTrue(docs.keySet().contains("snort_2"));
    assertEquals("bro", docs.get("bro_1").getDocument().get(getSourceTypeField()));
    assertEquals("snort", docs.get("snort_2").getDocument().get(getSourceTypeField()));
  }

  @Test
  public void filter_query_filters_results() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(filterQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(3, response.getTotal());
    List<SearchResult> results = response.getResults();
    assertEquals("snort", results.get(0).getSource().get(getSourceTypeField()));
    assertEquals("9", results.get(0).getSource().get("timestamp").toString());
    assertEquals("snort", results.get(1).getSource().get(getSourceTypeField()));
    assertEquals("7", results.get(1).getSource().get("timestamp").toString());
    assertEquals("bro", results.get(2).getSource().get(getSourceTypeField()));
    assertEquals("1", results.get(2).getSource().get("timestamp").toString());
  }

  @Test
  public void sort_query_sorts_results_ascending() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(sortQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    for (int i = 8001; i < 8011; ++i) {
      assertEquals(i, results.get(i - 8001).getSource().get("ip_src_port"));
    }
  }

  @Test
  public void sort_ascending_with_missing_fields() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(sortAscendingWithMissingFields, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    assertEquals(10, results.size());

    // the remaining are missing the 'threat:triage:score' and should be sorted last
    for (int i = 0; i < 8; i++) {
      assertFalse(results.get(i).getSource().containsKey("threat:triage:score"));
    }

    // validate sorted order - there are only 2 with a 'threat:triage:score'
    assertEquals("10.0", results.get(8).getSource().get("threat:triage:score").toString());
    assertEquals("20.0", results.get(9).getSource().get("threat:triage:score").toString());
  }

  @Test
  public void sort_descending_with_missing_fields() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(sortDescendingWithMissingFields, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    assertEquals(10, results.size());

    // validate sorted order - there are only 2 with a 'threat:triage:score'
    assertEquals("20.0", results.get(0).getSource().get("threat:triage:score").toString());
    assertEquals("10.0", results.get(1).getSource().get("threat:triage:score").toString());

    // the remaining are missing the 'threat:triage:score' and should be sorted last
    for (int i = 2; i < 10; i++) {
      assertFalse(results.get(i).getSource().containsKey("threat:triage:score"));
    }
  }

  @Test
  public void results_are_paginated() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(paginationQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    assertEquals(3, results.size());
    assertEquals("snort", results.get(0).getSource().get(getSourceTypeField()));
    assertEquals("6", results.get(0).getSource().get("timestamp").toString());
    assertEquals("bro", results.get(1).getSource().get(getSourceTypeField()));
    assertEquals("5", results.get(1).getSource().get("timestamp").toString());
    assertEquals("bro", results.get(2).getSource().get(getSourceTypeField()));
    assertEquals("4", results.get(2).getSource().get("timestamp").toString());
  }

  @Test
  public void returns_results_only_for_specified_indices() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(indexQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(5, response.getTotal());
    List<SearchResult> results = response.getResults();
    for (int i = 5, j = 0; i > 0; i--, j++) {
      assertEquals("bro", results.get(j).getSource().get(getSourceTypeField()));
      assertEquals(i + "", results.get(j).getSource().get("timestamp").toString());
    }
  }

  @Test
  public void facet_query_yields_field_types() throws Exception {
    String facetQuery = facetQueryRaw.replace("source:type", getSourceTypeField());
    SearchRequest request = JSONUtils.INSTANCE.load(facetQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    Map<String, Map<String, Long>> facetCounts = response.getFacetCounts();
    assertEquals(8, facetCounts.size());
    Map<String, Long> sourceTypeCounts = facetCounts.get(getSourceTypeField());
    assertEquals(2, sourceTypeCounts.size());
    assertEquals(new Long(5), sourceTypeCounts.get("bro"));
    assertEquals(new Long(5), sourceTypeCounts.get("snort"));
    Map<String, Long> ipSrcAddrCounts = facetCounts.get("ip_src_addr");
    assertEquals(8, ipSrcAddrCounts.size());
    assertEquals(new Long(3), ipSrcAddrCounts.get("192.168.1.1"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.2"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.3"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.4"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.5"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.6"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.7"));
    assertEquals(new Long(1), ipSrcAddrCounts.get("192.168.1.8"));
    Map<String, Long> ipSrcPortCounts = facetCounts.get("ip_src_port");
    assertEquals(10, ipSrcPortCounts.size());
    assertEquals(new Long(1), ipSrcPortCounts.get("8001"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8002"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8003"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8004"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8005"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8006"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8007"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8008"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8009"));
    assertEquals(new Long(1), ipSrcPortCounts.get("8010"));
    Map<String, Long> longFieldCounts = facetCounts.get("long_field");
    assertEquals(2, longFieldCounts.size());
    assertEquals(new Long(8), longFieldCounts.get("10000"));
    assertEquals(new Long(2), longFieldCounts.get("20000"));
    Map<String, Long> timestampCounts = facetCounts.get("timestamp");
    assertEquals(10, timestampCounts.size());
    assertEquals(new Long(1), timestampCounts.get("1"));
    assertEquals(new Long(1), timestampCounts.get("2"));
    assertEquals(new Long(1), timestampCounts.get("3"));
    assertEquals(new Long(1), timestampCounts.get("4"));
    assertEquals(new Long(1), timestampCounts.get("5"));
    assertEquals(new Long(1), timestampCounts.get("6"));
    assertEquals(new Long(1), timestampCounts.get("7"));
    assertEquals(new Long(1), timestampCounts.get("8"));
    assertEquals(new Long(1), timestampCounts.get("9"));
    assertEquals(new Long(1), timestampCounts.get("10"));
    Map<String, Long> latitudeCounts = facetCounts.get("latitude");
    assertEquals(2, latitudeCounts.size());
    List<String> latitudeKeys = new ArrayList<>(latitudeCounts.keySet());
    Collections.sort(latitudeKeys);
    assertEquals(48.0001, Double.parseDouble(latitudeKeys.get(0)), 0.00001);
    assertEquals(48.5839, Double.parseDouble(latitudeKeys.get(1)), 0.00001);
    assertEquals(new Long(2), latitudeCounts.get(latitudeKeys.get(0)));
    assertEquals(new Long(8), latitudeCounts.get(latitudeKeys.get(1)));
    Map<String, Long> scoreFieldCounts = facetCounts.get("score");
    assertEquals(4, scoreFieldCounts.size());
    List<String> scoreFieldKeys = new ArrayList<>(scoreFieldCounts.keySet());
    Collections.sort(scoreFieldKeys);
    assertEquals(10.0, Double.parseDouble(scoreFieldKeys.get(0)), 0.00001);
    assertEquals(20.0, Double.parseDouble(scoreFieldKeys.get(1)), 0.00001);
    assertEquals(50.0, Double.parseDouble(scoreFieldKeys.get(2)), 0.00001);
    assertEquals(98.0, Double.parseDouble(scoreFieldKeys.get(3)), 0.00001);
    assertEquals(new Long(4), scoreFieldCounts.get(scoreFieldKeys.get(0)));
    assertEquals(new Long(2), scoreFieldCounts.get(scoreFieldKeys.get(1)));
    assertEquals(new Long(3), scoreFieldCounts.get(scoreFieldKeys.get(2)));
    assertEquals(new Long(1), scoreFieldCounts.get(scoreFieldKeys.get(3)));
    Map<String, Long> isAlertCounts = facetCounts.get("is_alert");
    assertEquals(2, isAlertCounts.size());
    assertEquals(new Long(6), isAlertCounts.get("true"));
    assertEquals(new Long(4), isAlertCounts.get("false"));
  }

  @Test
  public void disabled_facet_query_returns_null_count() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(disabledFacetQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertNull(response.getFacetCounts());
  }

  @Test
  public void missing_type_facet_query() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(missingTypeFacetQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());

    Map<String, Map<String, Long>> facetCounts = response.getFacetCounts();
    assertEquals(1, facetCounts.size());
    Map<String, Long> snortFieldCounts = facetCounts.get("sig_generator");
    assertEquals(5, snortFieldCounts.size());

    assertEquals(1L, snortFieldCounts.get("sig_generator 5").longValue());
    assertEquals(1L, snortFieldCounts.get("sig_generator 4").longValue());
    assertEquals(1L, snortFieldCounts.get("sig_generator 3").longValue());
    assertEquals(1L, snortFieldCounts.get("sig_generator 2").longValue());
    assertEquals(1L, snortFieldCounts.get("sig_generator 1").longValue());
    response.getFacetCounts();
  }

  @Test
  public void different_type_facet_query() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(differentTypeFacetQuery, SearchRequest.class);
    assertThrows(InvalidSearchException.class, () -> getIndexDao().search(request));
  }

  @Test
  public void exceeding_max_results_throws_exception() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(exceededMaxResultsQuery, SearchRequest.class);
    InvalidSearchException e = assertThrows(InvalidSearchException.class, () -> getIndexDao().search(request));
    assertEquals("Search result size must be less than 100", e.getMessage());
  }

  @Test
  public void column_metadata_for_missing_index() throws Exception {
    // getColumnMetadata with an index that doesn't exist
    {
      Map<String, FieldType> fieldTypes = getIndexDao().getColumnMetadata(Collections.singletonList("someindex"));
      assertEquals(0, fieldTypes.size());
    }
  }

  @Test
  public void no_results_returned_when_query_does_not_match() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(noResultsFieldsQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(0, response.getTotal());
  }

  @Test
  public void group_by_ip_query() throws Exception {
    GroupRequest request = JSONUtils.INSTANCE.load(groupByIpQuery, GroupRequest.class);
    GroupResponse response = getIndexDao().group(request);

    // expect only 1 group for 'ip_src_addr'
    assertEquals("ip_src_addr", response.getGroupedBy());

    // there are 8 different 'ip_src_addr' values
    List<GroupResult> groups = response.getGroupResults();
    assertEquals(8, groups.size());

    // expect dotted-decimal notation in descending order
    assertEquals("192.168.1.8", groups.get(0).getKey());
    assertEquals("192.168.1.7", groups.get(1).getKey());
    assertEquals("192.168.1.6", groups.get(2).getKey());
    assertEquals("192.168.1.5", groups.get(3).getKey());
    assertEquals("192.168.1.4", groups.get(4).getKey());
    assertEquals("192.168.1.3", groups.get(5).getKey());
    assertEquals("192.168.1.2", groups.get(6).getKey());
    assertEquals("192.168.1.1", groups.get(7).getKey());
  }

  @Test
  public void group_by_returns_results_in_groups() throws Exception {
    // Group by test case, default order is count descending
    GroupRequest request = JSONUtils.INSTANCE.load(groupByQuery, GroupRequest.class);
    GroupResponse response = getIndexDao().group(request);
    assertEquals("is_alert", response.getGroupedBy());
    List<GroupResult> isAlertGroups = response.getGroupResults();
    assertEquals(2, isAlertGroups.size());

    // isAlert == true group
    GroupResult trueGroup = isAlertGroups.get(0);
    assertEquals("true", trueGroup.getKey());
    assertEquals(6, trueGroup.getTotal());
    assertEquals("latitude", trueGroup.getGroupedBy());
    assertEquals(198.0, trueGroup.getScore(), 0.00001);
    List<GroupResult> trueLatitudeGroups = trueGroup.getGroupResults();
    assertEquals(2, trueLatitudeGroups.size());


    // isAlert == true && latitude == 48.5839 group
    GroupResult trueLatitudeGroup2 = trueLatitudeGroups.get(0);
    assertEquals(48.5839, Double.parseDouble(trueLatitudeGroup2.getKey()), 0.00001);
    assertEquals(5, trueLatitudeGroup2.getTotal());
    assertEquals(148.0, trueLatitudeGroup2.getScore(), 0.00001);

    // isAlert == true && latitude == 48.0001 group
    GroupResult trueLatitudeGroup1 = trueLatitudeGroups.get(1);
    assertEquals(48.0001, Double.parseDouble(trueLatitudeGroup1.getKey()), 0.00001);
    assertEquals(1, trueLatitudeGroup1.getTotal());
    assertEquals(50.0, trueLatitudeGroup1.getScore(), 0.00001);

    // isAlert == false group
    GroupResult falseGroup = isAlertGroups.get(1);
    assertEquals("false", falseGroup.getKey());
    assertEquals("latitude", falseGroup.getGroupedBy());
    assertEquals(130.0, falseGroup.getScore(), 0.00001);
    List<GroupResult> falseLatitudeGroups = falseGroup.getGroupResults();
    assertEquals(2, falseLatitudeGroups.size());

    // isAlert == false && latitude == 48.5839 group
    GroupResult falseLatitudeGroup2 = falseLatitudeGroups.get(0);
    assertEquals(48.5839, Double.parseDouble(falseLatitudeGroup2.getKey()), 0.00001);
    assertEquals(3, falseLatitudeGroup2.getTotal());
    assertEquals(80.0, falseLatitudeGroup2.getScore(), 0.00001);

    // isAlert == false && latitude == 48.0001 group
    GroupResult falseLatitudeGroup1 = falseLatitudeGroups.get(1);
    assertEquals(48.0001, Double.parseDouble(falseLatitudeGroup1.getKey()), 0.00001);
    assertEquals(1, falseLatitudeGroup1.getTotal());
    assertEquals(50.0, falseLatitudeGroup1.getScore(), 0.00001);
  }

  @Test
  public void group_by_returns_results_in_sorted_groups() throws Exception {
    // Group by with sorting test case where is_alert is sorted by count ascending and ip_src_addr is sorted by term descending
    GroupRequest request = JSONUtils.INSTANCE.load(sortedGroupByQuery, GroupRequest.class);
    GroupResponse response = getIndexDao().group(request);
    assertEquals("is_alert", response.getGroupedBy());
    List<GroupResult> isAlertGroups = response.getGroupResults();
    assertEquals(2, isAlertGroups.size());

    // isAlert == false group
    GroupResult falseGroup = isAlertGroups.get(0);
    assertEquals(4, falseGroup.getTotal());
    assertEquals("ip_src_addr", falseGroup.getGroupedBy());
    List<GroupResult> falseIpSrcAddrGroups = falseGroup.getGroupResults();
    assertEquals(4, falseIpSrcAddrGroups.size());

    // isAlert == false && ip_src_addr == 192.168.1.8 group
    GroupResult falseIpSrcAddrGroup1 = falseIpSrcAddrGroups.get(0);
    assertEquals("192.168.1.8", falseIpSrcAddrGroup1.getKey());
    assertEquals(1, falseIpSrcAddrGroup1.getTotal());
    assertNull(falseIpSrcAddrGroup1.getGroupedBy());
    assertNull(falseIpSrcAddrGroup1.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.7 group
    GroupResult falseIpSrcAddrGroup2 = falseIpSrcAddrGroups.get(1);
    assertEquals("192.168.1.7", falseIpSrcAddrGroup2.getKey());
    assertEquals(1, falseIpSrcAddrGroup2.getTotal());
    assertNull(falseIpSrcAddrGroup2.getGroupedBy());
    assertNull(falseIpSrcAddrGroup2.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.6 group
    GroupResult falseIpSrcAddrGroup3 = falseIpSrcAddrGroups.get(2);
    assertEquals("192.168.1.6", falseIpSrcAddrGroup3.getKey());
    assertEquals(1, falseIpSrcAddrGroup3.getTotal());
    assertNull(falseIpSrcAddrGroup3.getGroupedBy());
    assertNull(falseIpSrcAddrGroup3.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.2 group
    GroupResult falseIpSrcAddrGroup4 = falseIpSrcAddrGroups.get(3);
    assertEquals("192.168.1.2", falseIpSrcAddrGroup4.getKey());
    assertEquals(1, falseIpSrcAddrGroup4.getTotal());
    assertNull(falseIpSrcAddrGroup4.getGroupedBy());
    assertNull(falseIpSrcAddrGroup4.getGroupResults());

    // isAlert == false group
    GroupResult trueGroup = isAlertGroups.get(1);
    assertEquals(6, trueGroup.getTotal());
    assertEquals("ip_src_addr", trueGroup.getGroupedBy());
    List<GroupResult> trueIpSrcAddrGroups = trueGroup.getGroupResults();
    assertEquals(4, trueIpSrcAddrGroups.size());

    // isAlert == false && ip_src_addr == 192.168.1.5 group
    GroupResult trueIpSrcAddrGroup1 = trueIpSrcAddrGroups.get(0);
    assertEquals("192.168.1.5", trueIpSrcAddrGroup1.getKey());
    assertEquals(1, trueIpSrcAddrGroup1.getTotal());
    assertNull(trueIpSrcAddrGroup1.getGroupedBy());
    assertNull(trueIpSrcAddrGroup1.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.4 group
    GroupResult trueIpSrcAddrGroup2 = trueIpSrcAddrGroups.get(1);
    assertEquals("192.168.1.4", trueIpSrcAddrGroup2.getKey());
    assertEquals(1, trueIpSrcAddrGroup2.getTotal());
    assertNull(trueIpSrcAddrGroup2.getGroupedBy());
    assertNull(trueIpSrcAddrGroup2.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.3 group
    GroupResult trueIpSrcAddrGroup3 = trueIpSrcAddrGroups.get(2);
    assertEquals("192.168.1.3", trueIpSrcAddrGroup3.getKey());
    assertEquals(1, trueIpSrcAddrGroup3.getTotal());
    assertNull(trueIpSrcAddrGroup3.getGroupedBy());
    assertNull(trueIpSrcAddrGroup3.getGroupResults());

    // isAlert == false && ip_src_addr == 192.168.1.1 group
    GroupResult trueIpSrcAddrGroup4 = trueIpSrcAddrGroups.get(3);
    assertEquals("192.168.1.1", trueIpSrcAddrGroup4.getKey());
    assertEquals(3, trueIpSrcAddrGroup4.getTotal());
    assertNull(trueIpSrcAddrGroup4.getGroupedBy());
    assertNull(trueIpSrcAddrGroup4.getGroupResults());
  }

  @Test
  public void queries_fields() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(fieldsQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(10, response.getTotal());
    List<SearchResult> results = response.getResults();
    for (int i = 0; i < 5; ++i) {
      Map<String, Object> source = results.get(i).getSource();
      assertEquals(1, source.size());
      assertNotNull(source.get("ip_src_addr"));
    }
    for (int i = 5; i < 10; ++i) {
      Map<String, Object> source = results.get(i).getSource();
      assertEquals(1, source.size());
      assertNotNull(source.get("ip_src_addr"));
    }
  }

  @Test
  public void sort_by_guid() throws Exception {
    SearchRequest request = JSONUtils.INSTANCE.load(sortByGuidQuery, SearchRequest.class);
    SearchResponse response = getIndexDao().search(request);
    assertEquals(5, response.getTotal());
    List<SearchResult> results = response.getResults();
    for (int i = 0; i < 5; ++i) {
      Map<String, Object> source = results.get(i).getSource();
      assertEquals(1, source.size());
      assertEquals(source.get("guid"), "bro_" + (i + 1));
    }
  }

  @AfterAll
  public static void stop() {
    indexComponent.stop();
  }

  @Test
  public abstract void returns_column_data_for_multiple_indices() throws Exception;
  @Test
  public abstract void returns_column_metadata_for_specified_indices() throws Exception;
  @Test
  public abstract void different_type_filter_query() throws Exception;

  protected abstract IndexDao getIndexDao();

  protected abstract String getSourceTypeField();

  protected abstract String getIndexName(String sensorType);
}
