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
package org.apache.metron.rest.service.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.DeleteBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.apache.curator.framework.api.SetDataBuilder;
import org.apache.metron.common.configuration.ConfigurationType;
import org.apache.metron.common.configuration.EnrichmentConfigurations;
import org.apache.metron.common.zookeeper.ConfigurationsCache;
import org.apache.metron.rest.RestException;
import org.apache.metron.rest.service.GlobalConfigService;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("ALL")
public class GlobalConfigServiceImplTest {
  CuratorFramework curatorFramework;
  GlobalConfigService globalConfigService;
  ConfigurationsCache cache;

  @BeforeEach
  public void setUp() {
    curatorFramework = mock(CuratorFramework.class);
    cache = mock(ConfigurationsCache.class);
    globalConfigService = new GlobalConfigServiceImpl(curatorFramework, cache);
  }


  @Test
  public void deleteShouldProperlyCatchNoNodeExceptionAndReturnFalse() throws Exception {
    DeleteBuilder builder = mock(DeleteBuilder.class);

    when(curatorFramework.delete()).thenReturn(builder);
    when(builder.forPath(ConfigurationType.GLOBAL.getZookeeperRoot())).thenThrow(KeeperException.NoNodeException.class);

    assertFalse(globalConfigService.delete());
  }

  @Test
  public void deleteShouldProperlyCatchNonNoNodeExceptionAndThrowRestException() throws Exception {
    DeleteBuilder builder = mock(DeleteBuilder.class);

    when(curatorFramework.delete()).thenReturn(builder);
    when(builder.forPath(ConfigurationType.GLOBAL.getZookeeperRoot())).thenThrow(Exception.class);

    assertThrows(RestException.class, () -> assertFalse(globalConfigService.delete()));
  }

  @Test
  public void deleteShouldReturnTrueWhenClientSuccessfullyCallsDelete() throws Exception {
    DeleteBuilder builder = mock(DeleteBuilder.class);
    when(curatorFramework.delete()).thenReturn(builder);

    assertTrue(globalConfigService.delete());
    verify(curatorFramework).delete();
  }

  @Test
  public void getShouldProperlyReturnGlobalConfig() throws Exception {
    final String config = "{\"k\":\"v\"}";
    final Map<String, Object> configMap = new HashMap<String, Object>() {{
      put("k", "v");
    }};

    EnrichmentConfigurations configs = new EnrichmentConfigurations(){
      @Override
      public Map<String, Object> getConfigurations() {
        return ImmutableMap.of(ConfigurationType.GLOBAL.getTypeName(), configMap);
      }
    };
    when(cache.get( eq(EnrichmentConfigurations.class)))
            .thenReturn(configs);

    assertEquals(configMap, globalConfigService.get());
  }

  @Test
  public void getShouldWrapNonNoNodeExceptionInRestException() throws Exception {
    GetDataBuilder getDataBuilder = mock(GetDataBuilder.class);
    when(getDataBuilder.forPath(ConfigurationType.GLOBAL.getZookeeperRoot())).thenThrow(Exception.class);

    when(curatorFramework.getData()).thenReturn(getDataBuilder);

    assertThrows(RestException.class, () -> globalConfigService.get());
  }

  @Test
  public void saveShouldWrapExceptionInRestException() throws Exception {
    SetDataBuilder setDataBuilder = mock(SetDataBuilder.class);
    when(setDataBuilder.forPath(ConfigurationType.GLOBAL.getZookeeperRoot(), "{ }".getBytes(
        StandardCharsets.UTF_8))).thenThrow(Exception.class);

    when(curatorFramework.setData()).thenReturn(setDataBuilder);

    assertThrows(RestException.class, () -> globalConfigService.save(new HashMap<>()));
  }

  @Test
  public void saveShouldReturnSameConfigThatIsPassedOnSuccessfulSave() throws Exception {
    SetDataBuilder setDataBuilder = mock(SetDataBuilder.class);
    when(setDataBuilder.forPath(ConfigurationType.GLOBAL.getZookeeperRoot(), "{ }".getBytes(StandardCharsets.UTF_8))).thenReturn(new Stat());

    when(curatorFramework.setData()).thenReturn(setDataBuilder);

    assertEquals(new HashMap<>(), globalConfigService.save(new HashMap<>()));
    verify(setDataBuilder).forPath(eq(ConfigurationType.GLOBAL.getZookeeperRoot()), eq("{ }".getBytes(StandardCharsets.UTF_8)));
  }
}
