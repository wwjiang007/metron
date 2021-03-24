#!/usr/bin/env bash
#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

DEPS=$(mvn dependency:list)
rc=$?
if [[ $rc != 0 ]]; then
  echo "ERROR:  Failed to run mvn dependency:list"
  DEPS=$(mvn dependency:list -PHDP-2.5.0.0)
  rc=$?
  if [[ $rc != 0 ]]; then
    echo "ERROR:  Failed to run mvn dependency:list -PHDP-2.5.0.0"
    exit $rc
  fi
fi

echo "$DEPS" | grep "^\[INFO\]   " | awk '{print $2}' | grep -v "org.apache" | grep -v "test" | grep -v "provided" | grep -v "runtime" | grep -v ":system" | sort | uniq
