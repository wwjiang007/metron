<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

This project builds packages that allow you to install Apache Metron on an APT-based operating system like Ubuntu.

If you are installing Metron using Ambari, these packages are necessary prerequisites when installing on an APT-based platform like Ubuntu.  Installing Metron using **only** these packages still leaves a considerable amount of configuration necessary to get Metron running.  Installing with Ambari automates these additional steps.

**WARNING**: These packages are a recent addition to Metron.  These have not undergone the same level of testing as the RPM packages.  Improvements and more rigorous testing of these packages is underway and will improve in future releases.  Until then, use these at your own risk.

### Prerequisites

* Docker: Please ensure that Docker is installed and that the daemon is running.

### Quick Start

1. Execute the following command from the project's root directory. This will build/package **all** of Metron prior to building the DEBs. See [Build Packages](#build-packages) below to only build the DEBs.
    ```
    mvn clean package -DskipTests -Pbuild-debs
    ```

1. The packages will be accessible from the following location once the build process completes.
    ```
    metron-deployment/packaging/docker/deb-docker/target
    ```

### Build Packages

If Metron has already been built, just the DEB packages can be built by executing the following commands.
    ```
    cd metron-deployment
    mvn clean package -Pbuild-debs
    ```

### How does this work?

Using the `build-debs` profile as shown above effectively automates the following steps.

1. Copy the tarball for each Metron sub-project to the `target` working directory.

1. Build a Docker image of a Ubuntu Trusty host called `docker-deb` that contains all of the tools needed to build the packages.
    ```
    docker build -t deb-docker .
    ```

1. Execute the `build.sh` script within a Docker container.  The argument passed to the build script is the current version of Metron.
    ```
    docker run -v `pwd`:/root deb-docker:latest /bin/bash -c ./build.sh <metron-version>
    ```

1. This results in the DEBs being generated within the `target` directory.
