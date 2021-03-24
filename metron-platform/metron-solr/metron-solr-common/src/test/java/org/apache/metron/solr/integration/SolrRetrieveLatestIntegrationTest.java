/*
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

package org.apache.metron.solr.integration;

import static org.apache.metron.solr.SolrConstants.SOLR_ZOOKEEPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.metron.common.Constants;
import org.apache.metron.indexing.dao.AccessConfig;
import org.apache.metron.indexing.dao.IndexDao;
import org.apache.metron.indexing.dao.search.GetRequest;
import org.apache.metron.indexing.dao.update.Document;
import org.apache.metron.solr.client.SolrClientFactory;
import org.apache.metron.solr.dao.SolrDao;
import org.apache.metron.solr.integration.components.SolrComponent;
import org.apache.solr.client.solrj.SolrServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class SolrRetrieveLatestIntegrationTest {

  private static SolrComponent solrComponent;

  protected static final String TEST_COLLECTION = "test";
  protected static final String TEST_SENSOR = "test_sensor";
  protected static final String BRO_SENSOR = "bro";
  protected final long expectedTimestamp = 123456789L;
  private static IndexDao dao;

  @BeforeAll
  public static void setupBeforeClass() throws Exception {
    solrComponent = new SolrComponent.Builder().build();
    solrComponent.start();
  }

  @BeforeEach
  public void setup() throws Exception {
    solrComponent.addCollection(TEST_COLLECTION, "./src/test/resources/config/test/conf");
    solrComponent.addCollection(BRO_SENSOR, "./src/main/config/schema/bro");

    AccessConfig accessConfig = new AccessConfig();
    Map<String, Object> globalConfig = new HashMap<>();
    globalConfig.put(SOLR_ZOOKEEPER, solrComponent.getZookeeperUrl());
    accessConfig.setGlobalConfigSupplier(() -> globalConfig);
    // Map the sensor name to the collection name for test.
    accessConfig.setIndexSupplier(s -> s.equals(TEST_SENSOR) ? TEST_COLLECTION : s);

    dao = new SolrDao();
    dao.init(accessConfig);
    addData(BRO_SENSOR, BRO_SENSOR, expectedTimestamp);
    addData(TEST_COLLECTION, TEST_SENSOR, expectedTimestamp);
  }

  @AfterEach
  public void reset() {
    solrComponent.reset();
  }

  @AfterAll
  public static void teardown() {
    SolrClientFactory.close();
    solrComponent.stop();
  }

  @Test
  public void testGetLatest() throws IOException {
    Document actual = dao.getLatest("message_1_bro", BRO_SENSOR);
    assertEquals(buildExpectedDocument(BRO_SENSOR, 1), actual);
  }

  @Test
  public void testGetMissing() throws IOException {
    Document actual = dao.getLatest("message_1_bro", TEST_SENSOR);
    assertNull(actual);
  }

  @Test
  public void testGetBrokenMapping() throws IOException {
    AccessConfig accessConfig = new AccessConfig();
    Map<String, Object> globalConfig = new HashMap<>();
    globalConfig.put(SOLR_ZOOKEEPER, solrComponent.getZookeeperUrl());
    accessConfig.setGlobalConfigSupplier(() -> globalConfig);
    // Map the sensor name to the collection name for test.
    accessConfig.setIndexSupplier(s -> null);

    dao = new SolrDao();
    dao.init(accessConfig);

    Document actual = dao.getLatest("message_1_bro", TEST_SENSOR);
    assertNull(actual);
  }

  @Test
  public void testGetLatestCollectionSensorDiffer() throws IOException {
    Document actual = dao.getLatest("message_1_test_sensor", TEST_SENSOR);
    assertEquals(buildExpectedDocument(TEST_SENSOR, 1), actual);
  }

  @Test
  public void testGetAllLatest() throws IOException {
    List<GetRequest> requests = new ArrayList<>();
    requests.add(buildGetRequest(BRO_SENSOR, 1));
    requests.add(buildGetRequest(BRO_SENSOR, 2));

    Iterable<Document> actual = dao.getAllLatest(requests);
    Document expected1 = buildExpectedDocument(BRO_SENSOR, 1);
    assertTrue(Iterables.contains(actual, expected1));

    Document expected2 = buildExpectedDocument(BRO_SENSOR, 2);
    assertTrue(Iterables.contains(actual, expected2));
    assertEquals(2, Iterables.size(actual));
  }

  @Test
  public void testGetAllLatestCollectionExplicitIndex() throws IOException {
    List<GetRequest> requests = new ArrayList<>();
    GetRequest getRequestOne = buildGetRequest(TEST_SENSOR, 1);
    // Explicitly use the incorrect index. This forces it to prefer the explicit index over the
    // implicit one.
    getRequestOne.setIndex(BRO_SENSOR);
    requests.add(getRequestOne);

    Iterable<Document> actual = dao.getAllLatest(requests);
    // Expect 0 because the explicit index was incorrect.
    assertEquals(0, Iterables.size(actual));
  }

  @Test
  public void testGetAllLatestCollectionSensorMixed() throws IOException {
    List<GetRequest> requests = new ArrayList<>();
    requests.add(buildGetRequest(TEST_SENSOR, 1));
    requests.add(buildGetRequest(BRO_SENSOR, 2));

    Iterable<Document> actual = dao.getAllLatest(requests);
    assertTrue(Iterables.contains(actual, buildExpectedDocument(TEST_SENSOR, 1)));
    assertTrue(Iterables.contains(actual, buildExpectedDocument(BRO_SENSOR, 2)));
    assertEquals(2, Iterables.size(actual));
  }

  @Test
  public void testGetAllLatestCollectionOneMissing() throws IOException {
    List<GetRequest> requests = new ArrayList<>();
    requests.add(buildGetRequest(TEST_SENSOR, 1));
    GetRequest brokenRequest= new GetRequest();
    brokenRequest.setGuid(buildGuid(BRO_SENSOR, 2));
    brokenRequest.setSensorType(TEST_SENSOR);
    requests.add(brokenRequest);

    Iterable<Document> actual = dao.getAllLatest(requests);
    assertTrue(Iterables.contains(actual, buildExpectedDocument(TEST_SENSOR, 1)));
    assertEquals(1, Iterables.size(actual));
  }

  protected Document buildExpectedDocument(String sensor, int i) {
    Map<String, Object> expectedMapOne = new HashMap<>();
    expectedMapOne.put("source.type", sensor);
    expectedMapOne.put(Constants.Fields.TIMESTAMP.getName(), expectedTimestamp);
    expectedMapOne.put(Constants.GUID, buildGuid(sensor, i));
    return new Document(expectedMapOne, buildGuid(sensor, i), sensor, expectedTimestamp);
  }

  protected GetRequest buildGetRequest(String sensor, int i) {
    GetRequest requestOne = new GetRequest();
    requestOne.setGuid(buildGuid(sensor, i));
    requestOne.setSensorType(sensor);
    return requestOne;
  }

  protected static void addData(String collection, String sensorName, Long timestamp)
      throws IOException, SolrServerException {
    List<Map<String, Object>> inputData = new ArrayList<>();
    for (int i = 0; i < 3; ++i) {
      final String name = buildGuid(sensorName, i);
      HashMap<String, Object> inputMap = new HashMap<>();
      inputMap.put("source.type", sensorName);
      inputMap.put(Constants.GUID, name);
      inputMap.put(Constants.Fields.TIMESTAMP.getName(), timestamp);
      inputData.add(inputMap);
    }
    solrComponent.addDocs(collection, inputData);
  }

  protected static String buildGuid(String sensorName, int i) {
    return "message_" + i + "_" + sensorName;
  }
}
