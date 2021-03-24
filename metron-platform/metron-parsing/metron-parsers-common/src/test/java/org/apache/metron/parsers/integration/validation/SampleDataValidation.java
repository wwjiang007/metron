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
package org.apache.metron.parsers.integration.validation;

import org.apache.metron.integration.utils.TestUtils;
import org.apache.metron.parsers.integration.ParserValidation;
import org.apache.metron.test.TestDataType;
import org.apache.metron.test.utils.SampleDataUtils;
import org.apache.metron.test.utils.ValidationUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleDataValidation implements ParserValidation {

  @Override
  public String getName() {
    return "Sample Data Validation";
  }

  @Override
  public void validate(String sensorType, List<byte[]> actualMessages) throws Exception {
    List<byte[]> expectedMessages = TestUtils.readSampleData(SampleDataUtils.getSampleDataPath("..", sensorType, TestDataType.PARSED));
    assertEquals(expectedMessages.size(), actualMessages.size());
    for (int i = 0; i < actualMessages.size(); i++) {
      String expectedMessage = new String(expectedMessages.get(i), StandardCharsets.UTF_8);
      String actualMessage = new String(actualMessages.get(i), StandardCharsets.UTF_8);
      try {
        ValidationUtils.assertJsonEqual(expectedMessage, actualMessage);
      } catch (Throwable t) {
        System.out.println("expected: " + expectedMessage);
        System.out.println("actual: " + actualMessage);
        throw t;
      }
    }
  }
}
