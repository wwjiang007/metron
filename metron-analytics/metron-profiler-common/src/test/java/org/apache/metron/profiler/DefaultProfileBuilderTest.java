/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.metron.profiler;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.metron.common.configuration.profiler.ProfileConfig;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.stellar.dsl.Context;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.apache.metron.stellar.common.utils.ConversionUtils.convert;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the ProfileBuilder class.
 */
public class DefaultProfileBuilderTest {

  /**
   * {
   *   "ip_src_addr": "10.0.0.1",
   *   "ip_dst_addr": "10.0.0.20",
   *   "value": 100,
   *   "timestamp": "2017-08-18 09:00:00"
   * }
   */
  @Multiline
  private static String input;
  private JSONObject message;
  private ProfileBuilder builder;
  private ProfileConfig definition;

  @BeforeEach
  public void setup() throws Exception {
    message = (JSONObject) new JSONParser().parse(input);
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *     "x": "100",
   *     "y": "200"
   *   },
   *   "result": "x + y"
   * }
   */
  @Multiline
  private static String testInitProfile;

  /**
   * Ensure that the 'init' block is executed correctly.
   */
  @Test
  public void testInit() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testInitProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate that x = 100, y = 200
    assertEquals(100 + 200, (int) convert(m.get().getProfileValue(), Integer.class));
  }

  /**
   * The 'init' block is executed only when the first message is received.  If no message
   * has been received, the 'init' block will not be executed.
   */
  @Test
  public void testInitWithNoMessage() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testInitProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate that x = 0 and y = 0 as no initialization occurred
    assertEquals(0, (int) convert(m.get().getProfileValue(), Integer.class));
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *     "x": "0",
   *     "y": "0"
   *   },
   *   "update": {
   *     "x": "x + 1",
   *     "y": "y + 2"
   *   },
   *   "result": "x + y"
   * }
   */
  @Multiline
  private static String testUpdateProfile;

  /**
   * Ensure that the 'update' expressions are executed for each message applied to the profile.
   */
  @Test
  public void testUpdate() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testUpdateProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    int count = 10;
    for(int i=0; i<count; i++) {

      // apply the message
      builder.apply(message, timestamp);

      // advance time
      timestamp += 5;
    }
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate that x=0, y=0 then x+=1, y+=2 for each message
    assertEquals(count*1 + count*2, (int) convert(m.get().getProfileValue(), Integer.class));
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": { "x": "100" },
   *   "result": "x"
   * }
   */
  @Multiline
  private static String testResultProfile;

  /**
   * Ensure that the result expression is executed on a flush.
   */
  @Test
  public void testResult() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testResultProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(100, (int) convert(m.get().getProfileValue(), Integer.class));
  }

  /**
   * Ensure that time advances properly on each flush.
   */
  @Test
  public void testProfilePeriodOnFlush() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testResultProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    {
      // apply a message and flush
      builder.apply(message, timestamp);
      Optional<ProfileMeasurement> m = builder.flush();
      assertTrue(m.isPresent());

      // validate the profile period
      ProfilePeriod expected = ProfilePeriod.fromTimestamp(timestamp, 10, TimeUnit.MINUTES);
      assertEquals(expected, m.get().getPeriod());
    }
    {
      // advance time by at least one period... about 10 minutes
      timestamp += TimeUnit.MINUTES.toMillis(10);

      // apply a message and flush again
      builder.apply(message, timestamp);
      Optional<ProfileMeasurement> m = builder.flush();
      assertTrue(m.isPresent());

      // validate the profile period
      ProfilePeriod expected = ProfilePeriod.fromTimestamp(timestamp, 10, TimeUnit.MINUTES);
      assertEquals(expected, m.get().getPeriod());
    }
  }


  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": { "x": "100" },
   *   "groupBy": ["x * 1", "x * 2"],
   *   "result": "100.0"
   * }
   */
  @Multiline
  private static String testGroupByProfile;

  /**
   * Ensure that the 'groupBy' expression is executed correctly.
   */
  @Test
  public void testGroupBy() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testGroupByProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(2, m.get().getGroups().size());
    assertEquals(100, m.get().getGroups().get(0));
    assertEquals(200, m.get().getGroups().get(1));
  }

  /**
   * {
   *   "profile": "test-profile",
   *   "foreach": "ip_src_addr",
   *   "init": { "x": "100" },
   *   "groupBy": ["profile","entity","start","end","duration","result"],
   *   "result": "100"
   * }
   */
  @Multiline
  private static String testStateAvailableToGroupBy;

  /**
   * The 'groupBy' expression should be able to reference information about the profile including
   * the profile name, entity name, start of period, end of period, duration, and result.
   */
  @Test
  public void testStateAvailableToGroupBy() throws Exception {

    // setup
    long timestamp = 1503081070340L;
    ProfilePeriod period = ProfilePeriod.fromTimestamp(timestamp, 10, TimeUnit.MINUTES);
    definition = JSONUtils.INSTANCE.load(testStateAvailableToGroupBy, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate all values that should be accessible by the groupBy expression(s)
    assertEquals(6, m.get().getGroups().size());
    assertEquals("test-profile", m.get().getGroups().get(0), "invalid profile");
    assertEquals("10.0.0.1", m.get().getGroups().get(1), "invalid entity");
    assertEquals(period.getStartTimeMillis(), m.get().getGroups().get(2), "invalid start");
    assertEquals(period.getEndTimeMillis(), m.get().getGroups().get(3), "invalid end");
    assertEquals(period.getDurationMillis(), m.get().getGroups().get(4), "invalid duration");
    assertEquals(100, m.get().getGroups().get(5), "invalid result");
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *     "x": "if exists(x) then x else 0",
   *     "y": "if exists(y) then y else 0"
   *   },
   *   "update": {
   *     "x": "x + 1",
   *     "y": "y + 2"
   *   },
   *   "result": "x + y"
   * }
   */
  @Multiline
  private static String testFlushProfile;

  @Test
  public void testFlushDoesNotClearsState() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testFlushProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute - accumulate some state then flush it
    int count = 10;
    for(int i=0; i<count; i++) {

      // apply the message
      builder.apply(message, timestamp);

      // advance time
      timestamp += 5;
    }
    builder.flush();

    // advance time beyond the current period
    timestamp += TimeUnit.MINUTES.toMillis(20);

    // apply another message to accumulate new state, then flush again to validate original state was cleared
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();

    // validate
    assertTrue(m.isPresent());
    assertEquals(33, m.get().getProfileValue());
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *     "x": "0",
   *     "y": "0"
   *   },
   *   "update": {
   *     "x": "x + 1",
   *     "y": "y + 2"
   *   },
   *   "result": "x + y"
   * }
   */
  @Multiline
  private static String testFlushProfileWithNaiveInit;

  @Test
  public void testFlushDoesNotClearsStateButInitDoes() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testFlushProfileWithNaiveInit, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute - accumulate some state then flush it
    int count = 10;
    for(int i=0; i<count; i++) {

      // apply a message
      builder.apply(message, timestamp);

      // advance time
      timestamp += 5;
    }
    builder.flush();

    // advance time beyond the current period
    timestamp += TimeUnit.MINUTES.toMillis(20);

    // apply another message to accumulate new state, then flush again to validate original state was cleared
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(3, m.get().getProfileValue());
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "result": "100"
   * }
   */
  @Multiline
  private static String testEntityProfile;

  /**
   * Ensure that the entity is correctly set on the resulting profile measurements.
   */
  @Test
  public void testEntity() throws Exception {

    // setup
    long timestamp = 100;
    final String entity = "10.0.0.1";
    definition = JSONUtils.INSTANCE.load(testFlushProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity(entity)
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(entity, m.get().getEntity());
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *      "x": "100"
   *   },
   *   "result": {
   *      "profile": "x"
   *   }
   * }
   */
  @Multiline
  private static String testResultWithProfileExpression;

  /**
   * Ensure that the result expression is executed on a flush.
   */
  @Test
  public void testResultWithProfileExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testResultWithProfileExpression, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(100, m.get().getProfileValue());
  }

  /**
   * {
   *   "profile": "test",
   *   "foreach": "ip_src_addr",
   *   "init": {
   *      "x": "100"
   *   },
   *   "result": {
   *      "profile": "x",
   *      "triage": {
   *        "zero": "x - 100",
   *        "hundred": "x"
   *      }
   *   }
   * }
   */
  @Multiline
  private static String testResultWithTriageExpression;

  /**
   * Ensure that the result expression is executed on a flush.
   */
  @Test
  public void testResultWithTriageExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(testResultWithTriageExpression, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());

    // validate
    assertEquals(0, m.get().getTriageValues().get("zero"));
    assertEquals(100, m.get().getTriageValues().get("hundred"));
    assertEquals(100, m.get().getProfileValue());
  }

  /**
   * {
   *   "profile": "bad-init",
   *   "foreach": "ip_src_addr",
   *   "init":   { "x": "2 / 0" },
   *   "update": { "x": "x + 1" },
   *   "result": "x + y",
   *   "groupBy": ["cheese"]
   * }
   */
  @Multiline
  private static String badInitProfile;

  @Test
  public void testBadInitExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badInitProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // due to the bad expression, there should be no result
    builder.apply(message, timestamp);
    assertFalse(builder.flush().isPresent());
  }

  /**
   * {
   *   "profile": "bad-simple-result",
   *   "foreach": "ip_src_addr",
   *   "init":   { "x": "0" },
   *   "update": { "x": "x + 1" },
   *   "result": "2 / 0",
   *   "groupBy": ["cheese"]
   * }
   */
  @Multiline
  private static String badSimpleResultProfile;

  @Test
  public void testBadResultExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badSimpleResultProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // due to the bad expression, there should be no result
    builder.apply(message, timestamp);
    assertFalse(builder.flush().isPresent());
  }

  /**
   * {
   *   "profile": "bad-groupBy",
   *   "foreach": "ip_src_addr",
   *   "init":   { "x": "0" },
   *   "update": { "x": "x + 1" },
   *   "result": "x",
   *   "groupBy": ["nonexistant"]
   * }
   */
  @Multiline
  private static String badGroupByProfile;

  @Test
  public void testBadGroupByExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badGroupByProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // due to the bad expression, there should be no result
    builder.apply(message, timestamp);
    assertFalse(builder.flush().isPresent());
  }

  /**
   * {
   *   "profile": "bad-result-profile",
   *   "foreach": "ip_src_addr",
   *   "init": { "x": "100" },
   *   "result": {
   *      "profile": "2 / 0",
   *      "triage": {
   *        "zero": "x - 100",
   *        "hundred": "x"
   *      }
   *   }
   * }
   */
  @Multiline
  private static String badResultProfile;

  @Test
  public void testBadResultProfileExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badResultProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // due to the bad expression, there should be no result
    builder.apply(message, timestamp);
    assertFalse(builder.flush().isPresent());
  }

  /**
   * {
   *   "profile": "bad-result-triage",
   *   "foreach": "ip_src_addr",
   *   "init": { "x": "100" },
   *   "result": {
   *      "profile": "x",
   *      "triage": {
   *        "zero": "x - 100",
   *        "hundred": "2 / 0"
   *      }
   *   }
   * }
   */
  @Multiline
  private static String badResultTriage;

  @Test
  public void testBadResultTriageExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badResultTriage, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // due to the bad expression, there should be no result
    builder.apply(message, timestamp);
    assertFalse(builder.flush().isPresent());
  }

  /**
   * {
   *   "profile": "bad-update",
   *   "foreach": "ip_src_addr",
   *   "init":   { "x": "0" },
   *   "update": { "x": "x + (2/0)" },
   *   "result": "x"
   * }
   */
  @Multiline
  private static String badUpdateProfile;

  /**
   * If the 'init' expression succeeds, but the 'update' fails, the profile should still flush.  We cannot
   * be sure if the 'update' is failing on every message or just one.  Since that is the case, the profile
   * flushes whatever data it has.
   */
  @Test
  public void testBadUpdateExpression() throws Exception {

    // setup
    long timestamp = 100;
    definition = JSONUtils.INSTANCE.load(badUpdateProfile, ProfileConfig.class);
    builder = new DefaultProfileBuilder.Builder()
            .withDefinition(definition)
            .withEntity("10.0.0.1")
            .withPeriodDuration(10, TimeUnit.MINUTES)
            .withContext(Context.EMPTY_CONTEXT())
            .build();

    // execute
    builder.apply(message, timestamp);

    // if the update expression fails, the profile should still flush.
    Optional<ProfileMeasurement> m = builder.flush();
    assertTrue(m.isPresent());
    assertEquals(0, m.get().getProfileValue());
  }
}
