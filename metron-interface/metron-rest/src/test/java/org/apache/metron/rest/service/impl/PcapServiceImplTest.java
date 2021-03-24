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

import org.adrianwalker.multilinestring.Multiline;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.metron.common.Constants;
import org.apache.metron.common.utils.JSONUtils;
import org.apache.metron.job.*;
import org.apache.metron.job.manager.InMemoryJobManager;
import org.apache.metron.job.manager.JobManager;
import org.apache.metron.pcap.PcapHelper;
import org.apache.metron.pcap.config.PcapOptions;
import org.apache.metron.pcap.filter.fixed.FixedPcapFilter;
import org.apache.metron.pcap.filter.query.QueryPcapFilter;
import org.apache.metron.rest.MetronRestConstants;
import org.apache.metron.rest.RestException;
import org.apache.metron.rest.config.PcapJobSupplier;
import org.apache.metron.rest.mock.MockPcapJob;
import org.apache.metron.rest.mock.MockPcapJobSupplier;
import org.apache.metron.rest.model.pcap.*;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("ALL")
//@PrepareForTest({PcapToPdmlScriptWrapper.class, ProcessBuilder.class})
public class PcapServiceImplTest {
  /**
   *<?xml version="1.0" encoding="utf-8"?>
   *<?xml-stylesheet type="text/xsl" href="pdml2html.xsl"?>
   *<pdml version="0" creator="wireshark/2.6.1" time="Thu Jun 28 14:14:38 2018" capture_file="/tmp/pcap-data-201806272004-289365c53112438ca55ea047e13a12a5+0001.pcap">
   *<packet>
   *<proto name="geninfo" pos="0" showname="General information" size="722" hide="no">
   *<field name="num" pos="0" show="1" showname="Number" value="1" size="722"/>
   *</proto>
   *<proto name="ip" showname="Internet Protocol Version 4, Src: 192.168.66.1, Dst: 192.168.66.121" size="20" pos="14" hide="yes">
   *<field name="ip.addr" showname="Source or Destination Address: 192.168.66.121" hide="yes" size="4" pos="30" show="192.168.66.121" value="c0a84279"/>
   *<field name="ip.flags" showname="Flags: 0x4000, Don&#x27;t fragment" size="2" pos="20" show="0x00004000" value="4000">
   *<field name="ip.flags.mf" showname="..0. .... .... .... = More fragments: Not set" size="2" pos="20" show="0" value="0" unmaskedvalue="4000"/>
   *</field>
   *</proto>
   *</packet>
   *</pdml>
   */
  @Multiline
  private String pdmlXml;

  /**
   *{
   "version": "0",
   "creator": "wireshark/2.6.1",
   "time": "Thu Jun 28 14:14:38 2018",
   "captureFile": "/tmp/pcap-data-201806272004-289365c53112438ca55ea047e13a12a5+0001.pcap",
   "packets": [
   {
   "protos": [
   {
   "name": "geninfo",
   "pos": "0",
   "showname": "General information",
   "size": "722",
   "hide": "no",
   "fields": [
   {
   "name": "num",
   "pos": "0",
   "showname": "Number",
   "size": "722",
   "value": "1",
   "show": "1"
   }
   ]
   },
   {
   "name": "ip",
   "pos": "14",
   "showname": "Internet Protocol Version 4, Src: 192.168.66.1, Dst: 192.168.66.121",
   "size": "20",
   "hide": "yes",
   "fields": [
   {
   "name": "ip.addr",
   "pos": "30",
   "showname": "Source or Destination Address: 192.168.66.121",
   "size": "4",
   "value": "c0a84279",
   "show": "192.168.66.121",
   "hide": "yes"
   },
   {
   "name": "ip.flags",
   "pos": "20",
   "showname": "Flags: 0x4000, Don't fragment",
   "size": "2",
   "value": "4000",
   "show": "0x00004000",
   "fields": [
   {
   "name": "ip.flags.mf",
   "pos": "20",
   "showname": "..0. .... .... .... = More fragments: Not set",
   "size": "2",
   "value": "0",
   "show": "0",
   "unmaskedvalue": "4000"
   }
   ]
   }
   ]
   }
   ]
   }
   ]
   }
   */
  @Multiline
  private String expectedPdml;

  Environment environment;
  Configuration configuration;
  MockPcapJobSupplier mockPcapJobSupplier;
  PcapToPdmlScriptWrapper pcapToPdmlScriptWrapper;

  @BeforeEach
  public void setUp() {
    environment = mock(Environment.class);
    configuration = new Configuration();
    mockPcapJobSupplier = new MockPcapJobSupplier();
    pcapToPdmlScriptWrapper = new PcapToPdmlScriptWrapper();

    when(environment.getProperty(MetronRestConstants.PCAP_BASE_PATH_SPRING_PROPERTY)).thenReturn("/base/path");
    when(environment.getProperty(MetronRestConstants.PCAP_BASE_INTERIM_RESULT_PATH_SPRING_PROPERTY)).thenReturn("/base/interim/result/path");
    when(environment.getProperty(MetronRestConstants.PCAP_FINAL_OUTPUT_PATH_SPRING_PROPERTY)).thenReturn("/final/output/path");
    when(environment.getProperty(MetronRestConstants.PCAP_PAGE_SIZE_SPRING_PROPERTY)).thenReturn("100");
    when(environment.getProperty(MetronRestConstants.PCAP_FINALIZER_THREADPOOL_SIZE_SPRING_PROPERTY)).thenReturn("2C");
    when(environment.getProperty(MetronRestConstants.PCAP_PDML_SCRIPT_PATH_SPRING_PROPERTY)).thenReturn("/path/to/pdml/script");
    when(environment.getProperty(MetronRestConstants.USER_JOB_LIMIT_SPRING_PROPERTY, Integer.class, 1)).thenReturn(1);
  }

  @Test
  public void submitShouldProperlySubmitFixedPcapRequest() throws Exception {
    when(environment.containsProperty(MetronRestConstants.PCAP_YARN_QUEUE_SPRING_PROPERTY)).thenReturn(true);
    when(environment.getProperty(MetronRestConstants.PCAP_YARN_QUEUE_SPRING_PROPERTY)).thenReturn("pcap");

    FixedPcapRequest fixedPcapRequest = new FixedPcapRequest();
    fixedPcapRequest.setBasePath("basePath");
    fixedPcapRequest.setBaseInterimResultPath("baseOutputPath");
    fixedPcapRequest.setFinalOutputPath("finalOutputPath");
    fixedPcapRequest.setStartTimeMs(1L);
    fixedPcapRequest.setEndTimeMs(2L);
    fixedPcapRequest.setNumReducers(2);
    fixedPcapRequest.setIpSrcAddr("ip_src_addr");
    fixedPcapRequest.setIpDstAddr("ip_dst_addr");
    fixedPcapRequest.setIpSrcPort(1000);
    fixedPcapRequest.setIpDstPort(2000);
    fixedPcapRequest.setProtocol("tcp");
    fixedPcapRequest.setPacketFilter("filter");
    fixedPcapRequest.setIncludeReverse(true);
    MockPcapJob mockPcapJob = new MockPcapJob();
    mockPcapJobSupplier.setMockPcapJob(mockPcapJob);
    JobManager jobManager = new InMemoryJobManager<>();

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    mockPcapJob.setStatus(new JobStatus()
            .withJobId("jobId")
            .withDescription("description")
            .withPercentComplete(0L)
            .withState(JobStatus.State.RUNNING));

    Map<String, String> expectedFields = new HashMap<String, String>() {{
      put(Constants.Fields.SRC_ADDR.getName(), "ip_src_addr");
      put(Constants.Fields.DST_ADDR.getName(), "ip_dst_addr");
      put(Constants.Fields.SRC_PORT.getName(), "1000");
      put(Constants.Fields.DST_PORT.getName(), "2000");
      put(Constants.Fields.PROTOCOL.getName(), "tcp");
      put(Constants.Fields.INCLUDES_REVERSE_TRAFFIC.getName(), "true");
      put(PcapHelper.PacketFields.PACKET_FILTER.getName(), "filter");
    }};
    PcapStatus expectedPcapStatus = new PcapStatus();
    expectedPcapStatus.setJobId("jobId");
    expectedPcapStatus.setJobStatus(JobStatus.State.RUNNING.name());
    expectedPcapStatus.setDescription("description");

    assertEquals(expectedPcapStatus, pcapService.submit("user", fixedPcapRequest));
    assertEquals(expectedPcapStatus, pcapService.jobStatusToPcapStatus(jobManager.getJob("user", "jobId").getStatus()));
    assertEquals("basePath", mockPcapJob.getBasePath());
    assertEquals("baseOutputPath", mockPcapJob.getBaseInterrimResultPath());
    assertEquals("finalOutputPath", mockPcapJob.getFinalOutputPath());
    assertEquals(1000000, mockPcapJob.getStartTimeNs());
    assertEquals(2000000, mockPcapJob.getEndTimeNs());
    assertEquals(2, mockPcapJob.getNumReducers());
    assertEquals(100, mockPcapJob.getRecPerFile());
    assertEquals("pcap", mockPcapJob.getYarnQueue());
    assertEquals("2C", mockPcapJob.getFinalizerThreadpoolSize());
    assertTrue(mockPcapJob.getFilterImpl() instanceof FixedPcapFilter.Configurator);
    Map<String, String> actualFixedFields = mockPcapJob.getFixedFields();
    assertEquals("ip_src_addr", actualFixedFields.get(Constants.Fields.SRC_ADDR.getName()));
    assertEquals("1000", actualFixedFields.get(Constants.Fields.SRC_PORT.getName()));
    assertEquals("ip_dst_addr", actualFixedFields.get(Constants.Fields.DST_ADDR.getName()));
    assertEquals("2000", actualFixedFields.get(Constants.Fields.DST_PORT.getName()));
    assertEquals("true", actualFixedFields.get(Constants.Fields.INCLUDES_REVERSE_TRAFFIC.getName()));
    assertEquals("tcp", actualFixedFields.get(Constants.Fields.PROTOCOL.getName()));
    assertEquals("filter", actualFixedFields.get(PcapHelper.PacketFields.PACKET_FILTER.getName()));
  }

  @Test
  public void submitShouldProperlySubmitWithDefaults() throws Exception {
    long beforeJobTime = System.currentTimeMillis();

    FixedPcapRequest fixedPcapRequest = new FixedPcapRequest();
    MockPcapJob mockPcapJob = new MockPcapJob();
    mockPcapJobSupplier.setMockPcapJob(mockPcapJob);
    JobManager jobManager = new InMemoryJobManager<>();

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    mockPcapJob.setStatus(new JobStatus()
            .withJobId("jobId")
            .withDescription("description")
            .withPercentComplete(0L)
            .withState(JobStatus.State.RUNNING));

    PcapStatus expectedPcapStatus = new PcapStatus();
    expectedPcapStatus.setJobId("jobId");
    expectedPcapStatus.setJobStatus(JobStatus.State.RUNNING.name());
    expectedPcapStatus.setDescription("description");

    assertEquals(expectedPcapStatus, pcapService.submit("user", fixedPcapRequest));
    assertEquals("/base/path", mockPcapJob.getBasePath());
    assertEquals("/base/interim/result/path", mockPcapJob.getBaseInterrimResultPath());
    assertEquals("/final/output/path", mockPcapJob.getFinalOutputPath());
    assertEquals(0, mockPcapJob.getStartTimeNs());
    assertTrue(beforeJobTime <= mockPcapJob.getEndTimeNs() / 1000000);
    assertTrue(System.currentTimeMillis() >= mockPcapJob.getEndTimeNs() / 1000000);
    assertEquals(10, mockPcapJob.getNumReducers());
    assertEquals(100, mockPcapJob.getRecPerFile());
    assertTrue(mockPcapJob.getFilterImpl() instanceof FixedPcapFilter.Configurator);
    assertEquals(new HashMap<>(), mockPcapJob.getFixedFields());
  }

  @Test
  public void submitShouldProperlySubmitQueryPcapRequest() throws Exception {
    QueryPcapRequest queryPcapRequest = new QueryPcapRequest();
    queryPcapRequest.setBasePath("basePath");
    queryPcapRequest.setBaseInterimResultPath("baseOutputPath");
    queryPcapRequest.setFinalOutputPath("finalOutputPath");
    queryPcapRequest.setStartTimeMs(1L);
    queryPcapRequest.setEndTimeMs(2L);
    queryPcapRequest.setNumReducers(2);
    queryPcapRequest.setQuery("query");
    MockPcapJob mockPcapJob = new MockPcapJob();
    mockPcapJobSupplier.setMockPcapJob(mockPcapJob);
    JobManager jobManager = new InMemoryJobManager<>();

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    mockPcapJob.setStatus(new JobStatus()
            .withJobId("jobId")
            .withDescription("description")
            .withPercentComplete(0L)
            .withState(JobStatus.State.RUNNING));

    String expectedFields = "query";
    PcapStatus expectedPcapStatus = new PcapStatus();
    expectedPcapStatus.setJobId("jobId");
    expectedPcapStatus.setJobStatus(JobStatus.State.RUNNING.name());
    expectedPcapStatus.setDescription("description");

    assertEquals(expectedPcapStatus, pcapService.submit("user", queryPcapRequest));
    assertEquals(expectedPcapStatus, pcapService.jobStatusToPcapStatus(jobManager.getJob("user", "jobId").getStatus()));
    assertEquals("basePath", mockPcapJob.getBasePath());
    assertEquals("baseOutputPath", mockPcapJob.getBaseInterrimResultPath());
    assertEquals("finalOutputPath", mockPcapJob.getFinalOutputPath());
    assertEquals(1000000, mockPcapJob.getStartTimeNs());
    assertEquals(2000000, mockPcapJob.getEndTimeNs());
    assertEquals(2, mockPcapJob.getNumReducers());
    assertEquals(100, mockPcapJob.getRecPerFile());
    assertTrue(mockPcapJob.getFilterImpl() instanceof QueryPcapFilter.Configurator);
    Map<String, String> actualFixedFields = mockPcapJob.getFixedFields();
    assertEquals("query", mockPcapJob.getQuery());
  }

  @Test
  public void submitShouldThrowExceptionOnRunningJobFound() throws Exception {
    PcapStatus runningStatus1 = new PcapStatus();
    runningStatus1.setJobStatus("RUNNING");
    runningStatus1.setJobId("jobId1");
    PcapStatus runningStatus2 = new PcapStatus();
    runningStatus2.setJobStatus("RUNNING");
    runningStatus2.setJobId("jobId2");

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    doReturn(Arrays.asList(runningStatus1, runningStatus2)).when(pcapService).getJobStatus("user", JobStatus.State.RUNNING);
    when(environment.getProperty(MetronRestConstants.USER_JOB_LIMIT_SPRING_PROPERTY, Integer.class, 1)).thenReturn(2);

    RestException e = assertThrows(RestException.class, () -> pcapService.submit("user", new FixedPcapRequest()));
    assertTrue(e.getMessage().contains("Cannot submit job because a job is already running.  Please contact the administrator to cancel job(s) with id(s) jobId"));
  }


  @Test
  public void fixedShouldThrowRestException() throws Exception {
    FixedPcapRequest fixedPcapRequest = new FixedPcapRequest();
    JobManager jobManager = mock(JobManager.class);
    PcapJobSupplier pcapJobSupplier = new PcapJobSupplier();
    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, pcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(jobManager.submit(pcapJobSupplier, "user")).thenThrow(new JobException("some job exception"));

    RestException e = assertThrows(RestException.class, () -> pcapService.submit("user", fixedPcapRequest));
    assertEquals("some job exception", e.getMessage());
  }

  @Test
  public void getStatusShouldProperlyReturnStatus() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    JobStatus actualJobStatus = new JobStatus()
            .withJobId("jobId")
            .withState(JobStatus.State.SUCCEEDED)
            .withDescription("description")
            .withPercentComplete(100.0);
    Pageable pageable = mock(Pageable.class);
    when(pageable.getSize()).thenReturn(2);
    when(mockPcapJob.getStatus()).thenReturn(actualJobStatus);
    when(mockPcapJob.isDone()).thenReturn(true);
    when(mockPcapJob.get()).thenReturn(pageable);
    when(jobManager.getJob("user", "jobId")).thenReturn(mockPcapJob);

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper);
    PcapStatus expectedPcapStatus = new PcapStatus();
    expectedPcapStatus.setJobId("jobId");
    expectedPcapStatus.setJobStatus(JobStatus.State.SUCCEEDED.name());
    expectedPcapStatus.setDescription("description");
    expectedPcapStatus.setPercentComplete(100.0);
    expectedPcapStatus.setPageTotal(2);

    assertEquals(expectedPcapStatus, pcapService.getJobStatus("user", "jobId"));
  }

  @Test
  public void getStatusShouldReturnNullOnMissingStatus() throws Exception {
    JobManager jobManager = new InMemoryJobManager();
    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), jobManager, pcapToPdmlScriptWrapper);

    assertNull(pcapService.getJobStatus("user", "jobId"));
  }

  @Test
  public void getStatusShouldThrowRestException() throws Exception {
    JobManager jobManager = mock(JobManager.class);
    when(jobManager.getJob("user", "jobId")).thenThrow(new JobException("some job exception"));

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), jobManager, pcapToPdmlScriptWrapper);
    RestException e = assertThrows(RestException.class, () -> pcapService.getJobStatus("user", "jobId"));
    assertEquals("some job exception", e.getMessage());
  }

  @Test
  public void getStatusForStateShouldProperlyReturnJobs() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    Statusable<Path> runningJob = mock(Statusable.class);
    JobStatus runningStatus = mock(JobStatus.class);
    when(runningStatus.getJobId()).thenReturn("runningJob");
    when(runningStatus.getState()).thenReturn(JobStatus.State.RUNNING);
    when(runningJob.getStatus()).thenReturn(runningStatus);

    Statusable<Path> failedJob = mock(Statusable.class);
    when(failedJob.getStatus()).thenThrow(new JobException("job exception"));

    Statusable<Path> succeededJob = mock(Statusable.class);
    JobStatus succeededStatus = mock(JobStatus.class);
    when(succeededStatus.getJobId()).thenReturn("succeededJob");
    when(succeededStatus.getState()).thenReturn(JobStatus.State.SUCCEEDED);
    when(succeededJob.isDone()).thenReturn(true);
    when(succeededJob.getStatus()).thenReturn(succeededStatus);
    Pageable<Path> succeededPageable = mock(Pageable.class);
    when(succeededPageable.getSize()).thenReturn(5);
    when(succeededJob.get()).thenReturn(succeededPageable);

    when(jobManager.getJobs("user")).thenReturn(Arrays.asList(runningJob, failedJob, succeededJob));

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper);

    PcapStatus expectedRunningPcapStatus = new PcapStatus();
    expectedRunningPcapStatus.setJobId("runningJob");
    expectedRunningPcapStatus.setJobStatus(JobStatus.State.RUNNING.name());
    assertEquals(expectedRunningPcapStatus, pcapService.getJobStatus("user", JobStatus.State.RUNNING).get(0));

    PcapStatus expectedFailedPcapStatus = new PcapStatus();
    expectedFailedPcapStatus.setJobStatus(JobStatus.State.FAILED.name());
    expectedFailedPcapStatus.setDescription("job exception");
    assertEquals(expectedFailedPcapStatus, pcapService.getJobStatus("user", JobStatus.State.FAILED).get(0));

    PcapStatus expectedSucceededPcapStatus = new PcapStatus();
    expectedSucceededPcapStatus.setJobId("succeededJob");
    expectedSucceededPcapStatus.setJobStatus(JobStatus.State.SUCCEEDED.name());
    expectedSucceededPcapStatus.setPageTotal(5);
    assertEquals(expectedSucceededPcapStatus, pcapService.getJobStatus("user", JobStatus.State.SUCCEEDED).get(0));
  }

  @Test
  public void killJobShouldKillJobAndReportStatus() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    JobStatus actualJobStatus = new JobStatus()
            .withJobId("jobId")
            .withState(JobStatus.State.KILLED)
            .withDescription("description")
            .withPercentComplete(100.0);
    Pageable pageable = mock(Pageable.class);
    when(pageable.getSize()).thenReturn(0);
    when(mockPcapJob.getStatus()).thenReturn(actualJobStatus);
    when(mockPcapJob.isDone()).thenReturn(true);
    when(mockPcapJob.get()).thenReturn(pageable);
    when(jobManager.getJob("user", "jobId")).thenReturn(mockPcapJob);

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper);
    PcapStatus status = pcapService.killJob("user", "jobId");
    verify(jobManager, times(1)).killJob("user", "jobId");
    assertThat(status.getJobStatus(), CoreMatchers.equalTo(JobStatus.State.KILLED.toString()));
  }

  @Test
  public void killNonExistentJobShouldReturnNull() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    doThrow(new JobNotFoundException("Not found test exception.")).when(jobManager).killJob("user", "jobId");

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper);
    PcapStatus status = pcapService.killJob("user", "jobId");
    verify(jobManager, times(1)).killJob("user", "jobId");
    assertNull(status);
  }

  @Test
  public void getPathShouldProperlyReturnPath() throws Exception {
    Path actualPath = new Path("/path");
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    Pageable pageable = mock(Pageable.class);
    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), jobManager, pcapToPdmlScriptWrapper);

    when(pageable.getSize()).thenReturn(2);
    when(mockPcapJob.isDone()).thenReturn(true);
    when(mockPcapJob.get()).thenReturn(pageable);
    when(pageable.getPage(0)).thenReturn(actualPath);
    when(jobManager.getJob("user", "jobId")).thenReturn(mockPcapJob);

    assertEquals("/path", pcapService.getPath("user", "jobId", 1).toUri().getPath());
  }

  @Test
  public void getPathShouldReturnNullOnInvalidPageSize() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    Pageable pageable = mock(Pageable.class);
    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), jobManager, pcapToPdmlScriptWrapper);

    when(pageable.getSize()).thenReturn(2);
    when(mockPcapJob.isDone()).thenReturn(true);
    when(mockPcapJob.get()).thenReturn(pageable);
    when(jobManager.getJob("user", "jobId")).thenReturn(mockPcapJob);

    assertNull(pcapService.getPath("user", "jobId", 0));
    assertNull(pcapService.getPath("user", "jobId", 3));
  }

  @Test
  public void getPdmlShouldGetPdml() throws Exception {
    Path path = new Path("./target");
    PcapToPdmlScriptWrapper pcapToPdmlScriptWrapper = spy(new PcapToPdmlScriptWrapper());
    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(true);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);
    doReturn(new ByteArrayInputStream(pdmlXml.getBytes(StandardCharsets.UTF_8))).when(pcapToPdmlScriptWrapper).getRawInputStream(fileSystem, path);
    ProcessBuilder pb = mock(ProcessBuilder.class);
    Process p = mock(Process.class);
    OutputStream outputStream = new ByteArrayOutputStream();
    when(p.getOutputStream()).thenReturn(outputStream);
    when(p.isAlive()).thenReturn(true);
    when(p.getInputStream()).thenReturn(new ByteArrayInputStream(pdmlXml.getBytes(StandardCharsets.UTF_8)));
    doReturn(pb).when(pcapToPdmlScriptWrapper).getProcessBuilder(any());
    when(pb.start()).thenReturn(p);

    assertEquals(JSONUtils.INSTANCE.load(expectedPdml, Pdml.class), pcapService.getPdml("user", "jobId", 1));
  }

  @Test
  public void getPdmlShouldReturnNullOnNonexistentPath() throws Exception {
    Path path = new Path("/some/path");

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(false);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);

    assertNull(pcapService.getPdml("user", "jobId", 1));
  }

  @Test
  public void getPdmlShouldThrowException() throws Exception {
    Path path = new Path("./target");
    PcapToPdmlScriptWrapper pcapToPdmlScriptWrapper = spy(new PcapToPdmlScriptWrapper());
    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(true);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);
    ProcessBuilder pb = mock(ProcessBuilder.class);
    doReturn(pb).when(pcapToPdmlScriptWrapper).getProcessBuilder("/path/to/pdml/script", "target");
    when(pb.start()).thenThrow(new IOException("some exception"));

    RestException e = assertThrows(RestException.class, () -> pcapService.getPdml("user", "jobId", 1));
    assertEquals("some exception", e.getMessage());
  }

  @Test
  public void getRawShouldProperlyReturnInputStream() throws Exception {
    FSDataInputStream inputStream = mock(FSDataInputStream.class);
    Path path = new Path("./target");
    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), new PcapToPdmlScriptWrapper()));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(true);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);
    when(fileSystem.open(path)).thenReturn(inputStream);

    assertEquals(inputStream, pcapService.getRawPcap("user", "jobId", 1));
  }

  @Test
  public void getRawShouldReturnNullOnInvalidPage() throws Exception {
    Path path = new Path("/some/path");

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();

    assertNull(pcapService.getRawPcap("user", "jobId", 1));
  }

  @Test
  public void getRawShouldReturnNullOnNonexistentPath() throws Exception {
    Path path = new Path("/some/path");

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(false);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);

    assertNull(pcapService.getRawPcap("user", "jobId", 1));
  }

  @Test
  public void getRawShouldThrowException() throws Exception {
    Path path = new Path("./target");
    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), new InMemoryJobManager<>(), pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    when(fileSystem.exists(path)).thenReturn(true);
    doReturn(path).when(pcapService).getPath("user", "jobId", 1);
    when(fileSystem.open(path)).thenThrow(new IOException("some exception"));

    RestException e = assertThrows(RestException.class, () -> pcapService.getRawPcap("user", "jobId", 1));
    assertEquals("some exception", e.getMessage());
  }

  @Test
  public void getConfigurationShouldProperlyReturnFixedFilterConfiguration() throws Exception {
    FixedPcapRequest fixedPcapRequest = new FixedPcapRequest();
    fixedPcapRequest.setBasePath("basePath");
    fixedPcapRequest.setBaseInterimResultPath("baseOutputPath");
    fixedPcapRequest.setFinalOutputPath("finalOutputPath");
    fixedPcapRequest.setStartTimeMs(1L);
    fixedPcapRequest.setEndTimeMs(2L);
    fixedPcapRequest.setNumReducers(2);
    fixedPcapRequest.setIpSrcAddr("ip_src_addr");
    fixedPcapRequest.setIpDstAddr("ip_dst_addr");
    fixedPcapRequest.setIpSrcPort(1000);
    fixedPcapRequest.setIpDstPort(2000);
    fixedPcapRequest.setProtocol("tcp");
    fixedPcapRequest.setPacketFilter("filter");
    fixedPcapRequest.setIncludeReverse(true);
    MockPcapJob mockPcapJob = new MockPcapJob();
    mockPcapJobSupplier.setMockPcapJob(mockPcapJob);
    JobManager jobManager = new InMemoryJobManager<>();

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    mockPcapJob.setStatus(new JobStatus()
                    .withJobId("jobId"));

    pcapService.submit("user", fixedPcapRequest);

    Map<String, Object> configuration = pcapService.getConfiguration("user", "jobId");
    assertEquals("basePath", configuration.get(PcapOptions.BASE_PATH.getKey()));
    assertEquals("finalOutputPath", configuration.get(PcapOptions.FINAL_OUTPUT_PATH.getKey()));
    assertEquals(1L, configuration.get(PcapOptions.START_TIME_MS.getKey()));
    assertEquals(2L, configuration.get(PcapOptions.END_TIME_MS.getKey()));
    assertEquals(2, configuration.get(PcapOptions.NUM_REDUCERS.getKey()));
    assertEquals("ip_src_addr", configuration.get(FixedPcapOptions.IP_SRC_ADDR.getKey()));
    assertEquals("ip_dst_addr", configuration.get(FixedPcapOptions.IP_DST_ADDR.getKey()));
    assertEquals(1000, configuration.get(FixedPcapOptions.IP_SRC_PORT.getKey()));
    assertEquals(2000, configuration.get(FixedPcapOptions.IP_DST_PORT.getKey()));
    assertEquals("tcp", configuration.get(FixedPcapOptions.PROTOCOL.getKey()));
    assertEquals("filter", configuration.get(FixedPcapOptions.PACKET_FILTER.getKey()));
    assertEquals(true, configuration.get(FixedPcapOptions.INCLUDE_REVERSE.getKey()));
  }

  @Test
  public void getConfigurationShouldProperlyReturnQueryFilterConfiguration() throws Exception {
    QueryPcapRequest queryPcapRequest = new QueryPcapRequest();
    queryPcapRequest.setBasePath("basePath");
    queryPcapRequest.setBaseInterimResultPath("baseOutputPath");
    queryPcapRequest.setFinalOutputPath("finalOutputPath");
    queryPcapRequest.setStartTimeMs(1L);
    queryPcapRequest.setEndTimeMs(2L);
    queryPcapRequest.setNumReducers(2);
    queryPcapRequest.setQuery("query");
    MockPcapJob mockPcapJob = new MockPcapJob();
    mockPcapJobSupplier.setMockPcapJob(mockPcapJob);
    JobManager jobManager = new InMemoryJobManager<>();

    PcapServiceImpl pcapService = spy(new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper));
    FileSystem fileSystem = mock(FileSystem.class);
    doReturn(fileSystem).when(pcapService).getFileSystem();
    mockPcapJob.setStatus(new JobStatus()
            .withJobId("jobId"));

    pcapService.submit("user", queryPcapRequest);

    Map<String, Object> configuration = pcapService.getConfiguration("user", "jobId");
    assertEquals("basePath", configuration.get(PcapOptions.BASE_PATH.getKey()));
    assertEquals("finalOutputPath", configuration.get(PcapOptions.FINAL_OUTPUT_PATH.getKey()));
    assertEquals(1L, configuration.get(PcapOptions.START_TIME_MS.getKey()));
    assertEquals(2L, configuration.get(PcapOptions.END_TIME_MS.getKey()));
    assertEquals(2, configuration.get(PcapOptions.NUM_REDUCERS.getKey()));
    assertEquals("query", configuration.get(QueryPcapOptions.QUERY.getKey()));
  }

  @Test
  public void getConfigurationShouldReturnEmptyMapOnMissingJob() throws Exception {
    MockPcapJob mockPcapJob = mock(MockPcapJob.class);
    JobManager jobManager = mock(JobManager.class);
    doThrow(new JobNotFoundException("Not found test exception.")).when(jobManager).getJob("user", "jobId");

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, mockPcapJobSupplier, jobManager, pcapToPdmlScriptWrapper);
    Map<String, Object> configuration = pcapService.getConfiguration("user", "jobId");
    assertEquals(new HashMap<>(), configuration);
  }

  @Test
  public void getConfigurationShouldThrowRestException() throws Exception {
    JobManager jobManager = mock(JobManager.class);
    when(jobManager.getJob("user", "jobId")).thenThrow(new JobException("some job exception"));

    PcapServiceImpl pcapService = new PcapServiceImpl(environment, configuration, new PcapJobSupplier(), jobManager, pcapToPdmlScriptWrapper);
    RestException e = assertThrows(RestException.class, () -> pcapService.getConfiguration("user", "jobId"));
    assertEquals("some job exception", e.getMessage());
  }

}
