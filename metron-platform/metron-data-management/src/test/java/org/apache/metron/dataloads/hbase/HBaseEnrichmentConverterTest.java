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

package org.apache.metron.dataloads.hbase;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.metron.enrichment.converter.EnrichmentConverter;
import org.apache.metron.enrichment.converter.EnrichmentKey;
import org.apache.metron.enrichment.converter.EnrichmentValue;
import org.apache.metron.enrichment.converter.HbaseConverter;
import org.apache.metron.enrichment.lookup.LookupKV;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class HBaseEnrichmentConverterTest {
    public static byte[] keyBytes = new byte[] {
            0x31,(byte)0xc2,0x49,0x05,0x6b,(byte)0xea,
            0x0e,0x59,(byte)0xe1,(byte)0xad,(byte)0xa0,0x24,
            0x55,(byte)0xa9,0x6b,0x63,0x00,0x06,
            0x64,0x6f,0x6d,0x61,0x69,0x6e,
            0x00,0x06,0x67,0x6f,0x6f,0x67,
            0x6c,0x65
    };

    EnrichmentKey key = new EnrichmentKey("domain", "google");
    EnrichmentValue value = new EnrichmentValue(
            new HashMap<String, Object>() {{
                put("foo", "bar");
                put("grok", "baz");
            }});
    LookupKV<EnrichmentKey, EnrichmentValue> results = new LookupKV(key, value);

    /**
     * IF this test fails then you have broken the key serialization in that your change has
     * caused a key to change serialization, so keys from previous releases will not be able to be found
     * under your scheme.  Please either provide a migration plan or undo this change.  DO NOT CHANGE THIS
     * TEST BLITHELY!
     */
    @Test
    public void testKeySerializationRemainsConstant() {
        byte[] raw = key.toBytes();
        assertArrayEquals(raw, keyBytes);
    }
    @Test
    public void testKeySerialization() {
        byte[] serialized = key.toBytes();

        EnrichmentKey deserialized = new EnrichmentKey();
        deserialized.fromBytes(serialized);
        assertEquals(key, deserialized);
    }

    @Test
    public void testPut() throws IOException {
        HbaseConverter<EnrichmentKey, EnrichmentValue> converter = new EnrichmentConverter();
        Put put = converter.toPut("cf", key, value);
        LookupKV<EnrichmentKey, EnrichmentValue> converted= converter.fromPut(put, "cf");
        assertEquals(results, converted);
    }
    @Test
    public void testResult() throws IOException {
        HbaseConverter<EnrichmentKey, EnrichmentValue> converter = new EnrichmentConverter();
        Result r = converter.toResult("cf", key, value);
        LookupKV<EnrichmentKey, EnrichmentValue> converted= converter.fromResult(r, "cf");
        assertEquals(results, converted);
    }

    @Test
    public void testGet() {
        HbaseConverter<EnrichmentKey, EnrichmentValue> converter = new EnrichmentConverter();
        Get get = converter.toGet("cf", key);
        assertArrayEquals(key.toBytes(), get.getRow());
    }
}
