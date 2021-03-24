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

package org.apache.metron.writer.hdfs;

import org.apache.metron.common.configuration.IndexingConfigurations;
import org.apache.metron.common.configuration.writer.IndexingWriterConfiguration;
import org.apache.metron.common.configuration.writer.WriterConfiguration;
import org.apache.metron.common.writer.BulkMessage;
import org.apache.storm.hdfs.bolt.format.DefaultFileNameFormat;
import org.apache.storm.hdfs.bolt.format.FileNameFormat;
import org.apache.storm.hdfs.bolt.sync.CountSyncPolicy;
import org.apache.storm.task.TopologyContext;
import org.json.simple.JSONObject;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

// Suppress ConstantConditions to avoid NPE warnings that only would occur on test failure anyway
@SuppressWarnings("ConstantConditions")
@EnableRuleMigrationSupport
public class HdfsWriterTest {
  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String SENSOR_NAME = "sensor";
  private static final String WRITER_NAME = "writerName";

  private File folder;
  private FileNameFormat testFormat;

  @BeforeAll
  public static void beforeAll() throws Exception {
    // See https://issues.apache.org/jira/browse/METRON-2036
    // The need for this should go away when JUnit 4.13 is released and we can upgrade.
    Thread.interrupted();
  }

  @BeforeEach
  public void setup() throws IOException {
    // Ensure each test has a unique folder to work with.
    folder = tempFolder.newFolder();
    testFormat = new DefaultFileNameFormat()
            .withPath(folder.toString())
            .withExtension(".json")
            .withPrefix("prefix-");
  }

  @Test
  public void testGetHdfsPathNull() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    Object result = writer.getHdfsPathExtension(SENSOR_NAME,null, message);
    writer.close();
    assertEquals(SENSOR_NAME, result);
  }

  @Test
  public void testGetHdfsPathEmptyString() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "", message);
    writer.close();
    assertEquals(SENSOR_NAME, result);
  }

  @Test
  public void testGetHdfsPathConstant() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "'new'", message);
    writer.close();
    assertEquals("new", result);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGetHdfsPathDirectVariable() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "test.key", message);
    writer.close();
    assertEquals("test.value", result);
  }

  @Test
  public void testGetHdfsPathFormatConstant() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "FORMAT('/test/folder/')", message);
    writer.close();
    assertEquals("/test/folder/", result);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGetHdfsPathFormatVariable() {
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    message.put("test.key.2", "test.value.2");
    message.put("test.key.3", "test.value.3");
    Object result = writer.getHdfsPathExtension(SENSOR_NAME,"FORMAT('%s/%s/%s', test.key, test.key.2, test.key.3)", message);
    writer.close();
    assertEquals("test.value/test.value.2/test.value.3", result);
  }

  @Test
  public void testSetsCorrectHdfsFilename() {
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());
    String filename = writer.fileNameFormat.getName(1,1);
    assertEquals("prefix-Xcom-7-1-1.json", filename);
    writer.close();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGetHdfsPathMultipleFunctions() {
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    message.put("test.key.2", "test.value.2");
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "FORMAT('%s', test.key)", message);
    assertEquals("test.value", result);

    result = writer.getHdfsPathExtension(SENSOR_NAME, "FORMAT('%s/%s', test.key, test.key.2)", message);
    assertEquals("test.value/test.value.2", result);

    result = writer.getHdfsPathExtension(SENSOR_NAME, "FORMAT('%s', test.key)", message);
    writer.close();
    assertEquals("test.value", result);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testGetHdfsPathStringReturned() {
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    Object result = writer.getHdfsPathExtension(SENSOR_NAME, "TO_UPPER(FORMAT(MAP_GET('key', {'key': 'AbC%s'}), test.key))", message);
    writer.close();
    assertEquals("ABCTEST.VALUE", result);
  }

  @Test
  public void testGetHdfsPathNonString() {
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, new IndexingConfigurations());
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    try {
      assertThrows(IllegalArgumentException.class, () -> writer.getHdfsPathExtension(SENSOR_NAME, "{'key':'value'}", message));
    } finally {
      writer.close();
    }
  }

  @Test
  public void testGetSourceHandlerOpenFilesMax() throws IOException {
    int maxFiles = 2;
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat)
            .withMaxOpenFiles(maxFiles);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    for(int i = 0; i < maxFiles; i++) {
      writer.getSourceHandler(SENSOR_NAME, Integer.toString(i), null);
    }
    writer.close();
  }

  @Test
  public void testGetSourceHandlerOpenFilesOverMax() throws IOException {
    int maxFiles = 2;
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat)
                                        .withMaxOpenFiles(maxFiles);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    for(int i = 0; i < maxFiles; i++) {
      writer.getSourceHandler(SENSOR_NAME, Integer.toString(i), null);
    }
    // Should fail on max files + 1
    try {
      assertThrows(
          IllegalStateException.class,
          () -> writer.getSourceHandler(SENSOR_NAME, Integer.toString(maxFiles + 1), null));
    } finally {
      writer.close();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testWriteNoOutputFunction() throws Exception {
    FileNameFormat format = new DefaultFileNameFormat()
            .withPath(folder.toString())
            .withExtension(".json")
            .withPrefix("prefix-");
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(format);
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    WriterConfiguration config = new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    message.put("test.key2", "test.value2");
    JSONObject message2 = new JSONObject();
    message2.put("test.key", "test.value3");
    message2.put("test.key2", "test.value2");
    List<BulkMessage<JSONObject>> messages = new ArrayList<BulkMessage<JSONObject>>() {{
      add(new BulkMessage("message1", message));
      add(new BulkMessage("message2", message2));
    }};

    writer.write(SENSOR_NAME, config, messages);
    writer.close();

    ArrayList<String> expected = new ArrayList<>();
    expected.add(message.toJSONString());
    expected.add(message2.toJSONString());
    Collections.sort(expected);

    // Default to just putting it in the base folder + the sensor name
    File outputFolder = new File(folder.getAbsolutePath() + "/" + SENSOR_NAME);
    assertTrue(outputFolder.exists() && outputFolder.isDirectory());
    assertEquals(1, outputFolder.listFiles().length);

    for(File file : outputFolder.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      Collections.sort(lines);
      assertEquals(expected, lines);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testWriteSingleFile() throws Exception {
    String function = "FORMAT('test-%s/%s', test.key, test.key)";
    WriterConfiguration config = buildWriterConfiguration(function);
    FileNameFormat format = new DefaultFileNameFormat()
            .withPath(folder.toString())
            .withExtension(".json")
            .withPrefix("prefix-");
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(format);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    // These two messages will be routed to the same folder, because test.key is the same
    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    message.put("test.key2", "test.value2");
    JSONObject message2 = new JSONObject();
    message2.put("test.key", "test.value");
    message2.put("test.key3", "test.value2");
    List<BulkMessage<JSONObject>> messages = new ArrayList<BulkMessage<JSONObject>>() {{
      add(new BulkMessage<>("message1", message));
      add(new BulkMessage<>("message2", message2));
    }};

    writer.write(SENSOR_NAME, config, messages);
    writer.close();

    ArrayList<String> expected = new ArrayList<>();
    expected.add(message.toJSONString());
    expected.add(message2.toJSONString());
    Collections.sort(expected);

    File outputFolder = new File(folder.getAbsolutePath() + "/test-test.value/test.value/");
    assertTrue(outputFolder.exists() && outputFolder.isDirectory());
    assertEquals(1, outputFolder.listFiles().length);

    for(File file : outputFolder.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      Collections.sort(lines);
      assertEquals(expected, lines);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testWriteMultipleFiles() throws Exception {
    String function = "FORMAT('test-%s/%s', test.key, test.key)";
    WriterConfiguration config = buildWriterConfiguration(function);
    FileNameFormat format = new DefaultFileNameFormat()
            .withPath(folder.toString())
            .withExtension(".json")
            .withPrefix("prefix-");
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(format);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    // These two messages will be routed to the same folder, because test.key is the same
    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    message.put("test.key2", "test.value2");
    JSONObject message2 = new JSONObject();
    message2.put("test.key", "test.value2");
    message2.put("test.key3", "test.value3");
    List<BulkMessage<JSONObject>> messages = new ArrayList<BulkMessage<JSONObject>>() {{
      add(new BulkMessage("message1", message));
      add(new BulkMessage("message2", message2));
    }};

    writer.write(SENSOR_NAME, config, messages);
    writer.close();

    ArrayList<String> expected1 = new ArrayList<>();
    expected1.add(message.toJSONString());
    Collections.sort(expected1);

    File outputFolder1 = new File(folder.getAbsolutePath() + "/test-test.value/test.value/");
    assertTrue(outputFolder1.exists() && outputFolder1.isDirectory());
    assertEquals(1, outputFolder1.listFiles().length);

    for(File file : outputFolder1.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      Collections.sort(lines);
      assertEquals(expected1, lines);
    }

    ArrayList<String> expected2 = new ArrayList<>();
    expected2.add(message2.toJSONString());
    Collections.sort(expected2);

    File outputFolder2 = new File(folder.getAbsolutePath() + "/test-test.value2/test.value2/");
    assertTrue(outputFolder2.exists() && outputFolder2.isDirectory());
    assertEquals(1, outputFolder2.listFiles().length);

    for(File file : outputFolder2.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      Collections.sort(lines);
      assertEquals(expected2, lines);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testWriteSingleFileWithNull() throws Exception {
    String function = "FORMAT('test-%s/%s', test.key, test.key)";
    WriterConfiguration config = buildWriterConfiguration(function);
    FileNameFormat format = new DefaultFileNameFormat()
            .withPath(folder.toString())
            .withExtension(".json")
            .withPrefix("prefix-");
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(format);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    // These two messages will be routed to the same folder, because test.key is the same
    JSONObject message = new JSONObject();
    message.put("test.key2", "test.value2");
    List<BulkMessage<JSONObject>> messages = new ArrayList<BulkMessage<JSONObject>>() {{
      add(new BulkMessage("message1", message));
    }};

    writer.write(SENSOR_NAME, config,messages);
    writer.close();

    ArrayList<String> expected = new ArrayList<>();
    expected.add(message.toJSONString());
    Collections.sort(expected);

    File outputFolder = new File(folder.getAbsolutePath() + "/test-null/null/");
    assertTrue(outputFolder.exists() && outputFolder.isDirectory());
    assertEquals(1, outputFolder.listFiles().length);

    for(File file : outputFolder.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      Collections.sort(lines);
      assertEquals(expected, lines);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testSingleFileIfNoStreamClosed() throws Exception {
    String function = "FORMAT('test-%s/%s', test.key, test.key)";
    WriterConfiguration config = buildWriterConfiguration(function);
    HdfsWriter writer = new HdfsWriter().withFileNameFormat(testFormat);
    writer.init(new HashMap<String, String>(), config);
    writer.initFileNameFormat(createTopologyContext());

    JSONObject message = new JSONObject();
    message.put("test.key", "test.value");
    List<BulkMessage<JSONObject>> messages = new ArrayList<BulkMessage<JSONObject>>() {{
      add(new BulkMessage("message1", message));
    }};

    CountSyncPolicy basePolicy = new CountSyncPolicy(5);
    ClonedSyncPolicyCreator creator = new ClonedSyncPolicyCreator(basePolicy);

    writer.write(SENSOR_NAME, config, messages);
    writer.write(SENSOR_NAME, config, messages);
    writer.close();

    File outputFolder = new File(folder.getAbsolutePath() + "/test-test.value/test.value/");

    // The message should show up twice, once in each file
    ArrayList<String> expected = new ArrayList<>();
    expected.add(message.toJSONString());
    expected.add(message.toJSONString());

    // Assert both messages are in the same file, because the stream stayed open
    assertEquals(1, outputFolder.listFiles().length);
    for (File file : outputFolder.listFiles()) {
      List<String> lines = Files.readAllLines(file.toPath());
      // One line per file
      assertEquals(2, lines.size());
      assertEquals(expected, lines);
    }
  }

  protected WriterConfiguration buildWriterConfiguration(String function) {
    IndexingConfigurations indexingConfig = new IndexingConfigurations();
    Map<String, Object> sensorIndexingConfig = new HashMap<>();
    Map<String, Object> writerIndexingConfig = new HashMap<>();
    writerIndexingConfig.put(IndexingConfigurations.OUTPUT_PATH_FUNCTION_CONF, function);
    sensorIndexingConfig.put(WRITER_NAME, writerIndexingConfig);
    indexingConfig.updateSensorIndexingConfig(SENSOR_NAME, sensorIndexingConfig);
    return new IndexingWriterConfiguration(WRITER_NAME, indexingConfig);
  }

  private TopologyContext createTopologyContext(){
      Map<Integer, String> taskToComponent = new HashMap<>();
      taskToComponent.put(7, "Xcom");
      return new TopologyContext(null, null, taskToComponent, null, null, null, null, null, 7, 6703, null, null, null, null, null, null);
  }
}
