#!/bin/bash
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

# Need to build before we can properly list dependencies
echo "Building Metron"
mvn install -T 2C -q -DskipTests=true \
  -Dmaven.javadoc.skip=true \
  -Dskip.npm \
  -B -V

echo "Determining dependencies"
DEPS=$(dev-utilities/build-utils/list_dependencies.sh)
rc=$?
if [[ $rc != 0 ]]; then
  echo "Failed to determine dependencies"
  exit $rc
fi
echo "$DEPS" | python dev-utilities/build-utils/verify_license.py ./dependencies_with_url.csv
rc=$?
if [[ $rc != 0 ]]; then
  echo "Finished with dependency issues. Please ensure all dependencies are in dependencies_with_url.csv"
  exit $rc
else
  echo "Finished dependencies."
fi
