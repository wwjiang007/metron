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
package org.apache.metron.dataloads.hbase.mr;

import com.google.common.collect.Iterables;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.PosixParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.metron.dataloads.bulk.LeastRecentlyUsedPruner;
import org.apache.metron.enrichment.converter.EnrichmentConverter;
import org.apache.metron.enrichment.converter.EnrichmentKey;
import org.apache.metron.enrichment.converter.EnrichmentValue;
import org.apache.metron.enrichment.lookup.EnrichmentLookup;
import org.apache.metron.enrichment.lookup.LookupKey;
import org.apache.metron.enrichment.lookup.accesstracker.BloomAccessTracker;
import org.apache.metron.enrichment.lookup.accesstracker.PersistentAccessTracker;
import org.apache.metron.test.utils.UnitTestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

public class LeastRecentlyUsedPrunerIntegrationTest {
    /** The test util. */
    private static HBaseTestingUtility testUtil;

    /** The test table. */
    private static Table testTable;
    private static Table atTable;
    private static final String tableName = "malicious_domains";
    private static final String cf = "cf";
    private static final String atTableName = "access_trackers";
    private static final String atCF= "cf";
    private static final String beginTime = "04/14/2016 12:00:00";
    private static final String timeFormat = "georgia";
    private static Configuration config = null;

    @BeforeAll
    public static void setup() throws Exception {
        UnitTestHelper.setJavaLoggingLevel(Level.SEVERE);
        Map.Entry<HBaseTestingUtility, Configuration> kv = HBaseUtil.INSTANCE.create(true);
        config = kv.getValue();
        testUtil = kv.getKey();
        testTable = testUtil.createTable(Bytes.toBytes(tableName), Bytes.toBytes(cf));
        atTable = testUtil.createTable(Bytes.toBytes(atTableName), Bytes.toBytes(atCF));
    }

    @AfterAll
    public static void teardown() throws Exception {
        HBaseUtil.INSTANCE.teardown(testUtil);
    }

    public List<LookupKey> getKeys(int start, int end) {
        List<LookupKey> keys = new ArrayList<>();
        for(int i = start;i < end;++i) {
            keys.add(new EnrichmentKey("type", "key-" + i));
        }
        return keys;
    }

    @Test
    public void testCommandLine() throws Exception {
        Configuration conf = HBaseConfiguration.create();

        String[] argv = {"-a 04/14/2016 12:00:00", "-f cf", "-t malicious_domains", "-u access_trackers",  "-v georgia", "-z cf"};
        String[] otherArgs = new GenericOptionsParser(conf, argv).getRemainingArgs();

        CommandLine cli = LeastRecentlyUsedPruner.BulkLoadOptions.parse(new PosixParser(), otherArgs);
        assertEquals(cf, LeastRecentlyUsedPruner.BulkLoadOptions.COLUMN_FAMILY.get(cli).trim());
        assertEquals(tableName,LeastRecentlyUsedPruner.BulkLoadOptions.TABLE.get(cli).trim());
        assertEquals(atTableName,LeastRecentlyUsedPruner.BulkLoadOptions.ACCESS_TABLE.get(cli).trim());
        assertEquals(atCF,LeastRecentlyUsedPruner.BulkLoadOptions.ACCESS_COLUMN_FAMILY.get(cli).trim());
        assertEquals(beginTime, LeastRecentlyUsedPruner.BulkLoadOptions.AS_OF_TIME.get(cli).trim());
        assertEquals(timeFormat, LeastRecentlyUsedPruner.BulkLoadOptions.AS_OF_TIME_FORMAT.get(cli).trim());
    }

    @Test
    public void test() throws Exception {
        long ts = System.currentTimeMillis();
        BloomAccessTracker bat = new BloomAccessTracker("tracker1", 100, 0.03);
        PersistentAccessTracker pat = new PersistentAccessTracker(tableName, "0", atTable, atCF, bat, 0L);
        EnrichmentLookup lookup = new EnrichmentLookup(testTable, cf, pat);
        List<LookupKey> goodKeysHalf = getKeys(0, 5);
        List<LookupKey> goodKeysOtherHalf = getKeys(5, 10);
        Iterable<LookupKey> goodKeys = Iterables.concat(goodKeysHalf, goodKeysOtherHalf);
        List<LookupKey> badKey = getKeys(10, 11);
        EnrichmentConverter converter = new EnrichmentConverter();
        for(LookupKey k : goodKeysHalf) {
            testTable.put(converter.toPut(cf, (EnrichmentKey) k
                                            , new EnrichmentValue(
                                                  new HashMap<String, Object>() {{
                                                    put("k", "dummy");
                                                    }}
                                                  )
                                          )
                         );
            assertTrue(lookup.exists((EnrichmentKey)k, new EnrichmentLookup.HBaseContext(testTable, cf), true));
        }
        pat.persist(true);
        for(LookupKey k : goodKeysOtherHalf) {
            testTable.put(converter.toPut(cf, (EnrichmentKey) k
                                            , new EnrichmentValue(new HashMap<String, Object>() {{
                                                    put("k", "dummy");
                                                    }}
                                                                  )
                                         )
                         );
            assertTrue(lookup.exists((EnrichmentKey)k, new EnrichmentLookup.HBaseContext(testTable, cf), true));
        }
        testUtil.flush();
        assertFalse(lookup.getAccessTracker().hasSeen(goodKeysHalf.get(0)));
        for(LookupKey k : goodKeysOtherHalf) {
            assertTrue(lookup.getAccessTracker().hasSeen(k));
        }
        pat.persist(true);
        {
            testTable.put(converter.toPut(cf, (EnrichmentKey) badKey.get(0)
                    , new EnrichmentValue(new HashMap<String, Object>() {{
                        put("k", "dummy");
                    }}
                    )
                    )
            );
        }
        testUtil.flush();
        assertFalse(lookup.getAccessTracker().hasSeen(badKey.get(0)));


        Job job = LeastRecentlyUsedPruner.createJob(config, tableName, cf, atTableName, atCF, ts);
        assertTrue(job.waitForCompletion(true));
        for(LookupKey k : goodKeys) {
            assertTrue(lookup.exists((EnrichmentKey)k, new EnrichmentLookup.HBaseContext(testTable, cf), true));
        }
        for(LookupKey k : badKey) {
            assertFalse(lookup.exists((EnrichmentKey)k, new EnrichmentLookup.HBaseContext(testTable, cf), true));
        }

    }

}
