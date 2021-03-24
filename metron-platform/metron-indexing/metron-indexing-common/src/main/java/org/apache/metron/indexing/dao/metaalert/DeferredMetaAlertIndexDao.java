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

package org.apache.metron.indexing.dao.metaalert;

import org.apache.metron.indexing.dao.IndexDao;

/**
 * Interface for a DAO that is allowed to defer to a child Index DAO in order to perform tasks.
 * An example is metaalerts deferring to a base DAO.
 */
public interface DeferredMetaAlertIndexDao {

  IndexDao getIndexDao();

  String getMetAlertSensorName();

  String getMetaAlertIndex();

  default String getThreatTriageField() {
    return MetaAlertConstants.THREAT_FIELD_DEFAULT;
  }

  default String getThreatSort() {
    return MetaAlertConstants.THREAT_SORT_DEFAULT;
  }
}
