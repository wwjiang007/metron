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
package org.apache.metron.test.bolt;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import org.apache.curator.framework.CuratorFramework;
import org.apache.metron.zookeeper.ZKCache;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

public abstract class BaseBoltTest {
  
  @Mock
  protected TopologyContext topologyContext;

  @Mock
  protected OutputCollector outputCollector;

  @Mock
  protected Tuple tuple;

  @Mock
  protected OutputFieldsDeclarer declarer;

  @Mock
  protected CuratorFramework client;

  @Mock
  protected ZKCache cache;

  @BeforeEach
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  protected static class FieldsMatcher implements ArgumentMatcher<Fields> {

    private List<String> expectedFields;

    public FieldsMatcher(String... fields) {
      this.expectedFields = Arrays.asList(fields);
    }

    @Override
    public boolean matches(Fields o) {
      return expectedFields.equals(o.toList());
    }

    @Override
    public String toString() {
        return String.format("[%s]", Joiner.on(",").join(expectedFields));
    }
  }

  public void removeTimingFields(JSONObject message) {
    ImmutableSet keys = ImmutableSet.copyOf(message.keySet());
    for (Object key : keys) {
      if (key.toString().endsWith(".ts")) {
        message.remove(key);
      }
    }
  }
}
