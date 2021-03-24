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
# Upgrading
This document constitutes a per-version listing of changes of
configuration which are non-backwards compatible.


## 0.7.2 to 0.7.3

### [METRON-2239: Metron Automated backup and restore](https://issues.apache.org/jira/browse/METRON-2239)
An upgrade helper script has been added to `$METRON_HOME/bin/upgrade_helper.sh`. This script will assist in backing up and restoring Ambari configuration and Metron configuration stored in Zookeeper. You can see more details at [Metron Upgrade Helper](metron-platform/metron-common#metron-upgrade-helper) and [Upgrade Steps](Upgrade_steps.md).

### [METRON-2321: Remove Legacy AWS Deployment Path](https://issues.apache.org/jira/browse/METRON-2321)
The automated Amazon AWS deployment mechanism (previously located at `metron-deployment/amazon-ec2`) has been removed.  It is not the preferred installation path for deploying to AWS. Using Ambari and the Metron MPack is the preferred installation path. To deploy Metron to AWS, provision EC2 nodes, install Ambari, install the Metron MPack, then use Ambari to deploy Metron.

### [METRON-614: Eliminate use of the default Charset](https://issues.apache.org/jira/browse/METRON-614)
The use of the system default Charset is being dropped throughout the code in favor or explicit Charsets, by default UTF-8. As part of this change, individual parsers may now set a configuration property `readCharset` to allow each parser to set the Charset used by its data.  This has potential upgrade implications:

* If your system's default Charset and the input data is not UTF-8, you will need to configure each parser to use the appropriate Charset.
* If you've directly implemented the `MessageParser` interface (instead of extending `BasicParser`) and you need to handle non-UTF-8 Charsets, you should do reading and handling of this configuration in your implementation by overriding `getReadCharset()`.

## 0.7.1 to 0.7.2

### [METRON-2164: Remove the Split-Join Enrichment Topology](https://issues.apache.org/jira/browse/METRON-2164)
The Split-Join Enrichment topology has been deprecated since November 2018. Metron has defaulted to using the Unified Enrichment topology since that time. All users of the Split-Join Enrichment topology should migrate to the Unified Enrichment topology. Both topologies provide equivalent functionality.

## 0.7.0 to 0.7.1

### [METRON-2100: Update developer documentation for full dev management UI parser aggregation feature gap](https://issues.apache.org/jira/browse/METRON-2100)
The original full_dev environment change was actually introduced in Metron 0.7.0. This Jira addresses missing user documentation for the Management UI feature gap for parser aggregation. See [Parser Aggregation Feature](metron-deployment#parser-aggregation-feature) for more details on how to work with and configure parsers with this feature change enabled in full_dev.

### [METRON-2053: Refactor metron-enrichment to decouple Storm dependencies](https://issues.apache.org/jira/browse/METRON-2053)
`org.apache.metron.enrichment.writer.SimpleHbaseEnrichmentWriter` has had its packaged changed to `org.apache.metron.writer.hbase.SimpleHbaseEnrichmentWriter`. It has also been moved from the `metron-platform/metron-enrichment` module to a more appropriate home in `metron-platform/metron-writer`.

### [METRON-1929: Build GET_ASN Stellar function](https://issues.apache.org/jira/browse/METRON-1929)
The script for `geo_enrichment_load.sh` has been renamed, and now is `maxmind_enrichment_load.sh`. A couple changes should happen for users who are upgrading.

* The MaxMind GeoLite2 ASN database should be loaded onto HDFS at /apps/metron/asn/default/GeoLite2-ASN.tar.gz OR the global configuration property `asn.hdfs.file` can be set to point to a custom HDFS location.
* Any custom scripts or tasks that use this script should be updated. In addition, this updated script also retrieves the GeoLite2 ASN database.  The `-ra` flag can be used to provide a custom location for this database if offline install is needed. Otherwise, it will retrieve the latest from MaxMind.


## 0.6.0 to 0.7.0

### [METRON-1834: Migrate Elasticsearch from TransportClient to new Java REST API](https://issues.apache.org/jira/browse/METRON-1834)
The Elasticsearch Java client has now been migrated from TransportClient to the new Java REST client. The motivation for this change
is that TransportClient will be deprecated in Elasticsearch 7.0 and removed entirely in 8.0. See [ES Java API ](https://www.elastic.co/guide/en/elasticsearch/client/java-api/5.6/client.html) for more details.
The primary client-facing change for upgrades will be the new properties for configuring the new client. An explanation of the new properties
as well as a mapping from the old properties to the new can be found in [metron-elasticsearch](metron-platform/metron-elasticsearch/README.md#Properties) under `es.client.settings`.


### [METRON-1855: Make unified enrichment topology the default and deprecate split-join](https://issues.apache.org/jira/browse/METRON-1855)
The unified enrichment topology will be the new default in this release,
and the split-join enrichment topology is now considered deprecated.
If you wish to keep the deprecated split-join enrichment topology,
you will need to make the following changes:

* In Ambari > Metron > Config > Enrichment set the enrichment_topology setting to "Split-Join"
* If running `start_enrichment_topology.sh` manually, pass in the parameters to start the Split-Join topology as follows

    ```
    $METRON_HOME/bin/start_enrichment_topology.sh --remote $METRON_HOME/flux/enrichment/remote-splitjoin.yaml --filter $METRON_HOME/config/enrichment-splitjoin.properties
    ```

* Restart the enrichment topology

## 0.4.2 to 0.5.0

### [METRON-941: native PaloAlto parser corrupts message when having a comma in the payload](https://issues.apache.org/jira/browse/METRON-941)
While modifying the PaloAlto log parser to support logs from newer
PAN-OS version and to not break when a message payload contains a
comma, some field names were changed to extend the coverage, fix some
duplicate names and change some field names to the Metron standard
message format.

Installations making use of this parser should check, if the resulting
messages still meet their expectations and adjust downstream configurations
(i.e. ElasticSearch template) accordingly.

*Note:* Previously, the samples for the test contained a full syslog line
(including syslog header). This did - and will continue to - create a
broken "domain" field in the parsed message. It is recommended to only feed
the syslog message part to the parser for now.

## 0.4.1 to 0.4.2

### [METRON-1277: STELLAR Add Match functionality to language](https://issues.apache.org/jira/browse/METRON-1277)
As we continue to evolve the Stellar language, it is possible that new keywords
will be added to the language.  This may cause compatablity issues where these
reserved words and symbols are used in existing scripts.

Adding `match` to the Stellar lanaguage has introduced the following new
reserved keywords and symbols:

`match`, `default`, `{`, `}`, '=>'

Any stellar expressions which use these keywords not in quotes will need to be
modified.

### [METRON-1158: Build backend for grouping alerts into meta alerts](https://issues.apache.org/jira/browse/METRON-1158)
In order to allow for meta alerts to be queries alongside regular alerts in Elasticsearch 2.x,
it is necessary to add an additional field to the templates and mapping for existing sensors.

Two steps must be done for each sensor, but not on each index for each sensor.

First is to update the Elasticsearch template for each sensor, so any new indices have the field:

```
export ELASTICSEARCH="node1"
export SENSOR="bro"
curl -XGET "http://${ELASTICSEARCH}:9200/_template/${SENSOR}_index*?pretty=true" -o "${SENSOR}.template"
sed -i '2d;$d' ./${SENSOR}.template
sed -i '/"properties" : {/ a\
"alert": { "type": "nested"},' ${SENSOR}.template
curl -XPUT "http://${ELASTICSEARCH}:9200/_template/${SENSOR}_index" -d @${SENSOR}.template
```

To update existing indexes, update Elasticsearch mappings with the new field for each sensor.  Make sure to set the ELASTICSEARCH variable appropriately.

```
curl -XPUT "http://${ELASTICSEARCH}:9200/${SENSOR}_index*/_mapping/${SENSOR}_doc" -d '
{
        "properties" : {
          "alert" : {
            "type" : "nested"
          }
        }
}
'
rm ${SENSOR}.template
```

For a more detailed description, please see metron-platform/metron-elasticsearch/README.md

### Description

In the 0.4.2 release, 

## 0.3.1 to 0.4.0

### [METRON-671: Refactor existing Ansible deployment to use Ambari MPack](https://issues.apache.org/jira/browse/METRON-671)

#### Description
Since the Ansible Deployment uses the MPack, RPMs must be built prior to deployment. As a result,
[Docker](https://www.docker.com/) is required to perform a Quick-Dev, Full-Dev or Ansible deployment.
This effectively limits the build environment to Docker supported [platforms](https://docs.docker.com/engine/installation/#platform-support-matrix).

## 0.3.0 to 0.3.1

### [METRON-664: Make the index configuration per-writer with enabled/disabled](https://issues.apache.org/jira/browse/METRON-664)

#### Description

As of 0.3.0 the indexing configuration
* Is held in the enrichment configuration for a sensor 
* Has properties which control every writers (i.e. HDFS, solr or elasticsearch).

In the 0.3.1 release, this configuration has been broken out
and control for individual writers are separated.

Please see the description of the configurations in the indexing [README](https://github.com/apache/metron/tree/Metron_0.3.1/metron-platform/metron-indexing#sensor-indexing-configuration)

#### Migration

Migrate the configurations from each sensor enrichment configuration and create appropriate configurations for indexing.

For instance, if a sensor enrichment config for sensor `foo`
is in `$METRON_HOME/config/zookeeper/enrichments/foo.json` and looks like
```
{
  "index" : "foo",
  "batchSize" : 100
}
```

You would create a file to configure each writer for sensor `foo` called `$METRON_HOME/config/zookeeper/indexing/foo.json` with the contents
```
{
  "elasticsearch" : {
    "index" : "foo",
    "batchSize" : 100,
    "enabled" : true
  },
  "hdfs" : { 
    "index" : "foo",
    "batchSize" : 100,
    "enabled" : true
  }
}
```

### [METRON-675: Make Threat Triage rules able to be assigned names and comments](https://issues.apache.org/jira/browse/METRON-675)

#### Description

As of 0.3.0, threat triage rules were defined as a simple Map associating a Stellar expression with a score.
As of 0.3.1, due to the fact that there may be many threat triage rules, we have made the rules more complex.
To help organize these, we have made the threat triage objects in their own right that contain optional name and optional comment fields.
   
This essentially makes the risk level rules slightly more complex.  The format goes from:
```
"riskLevelRules" : {
    "stellar expression" : numeric score
}
```
to:
```
"riskLevelRules" : [
     {
        "name" : "optional name",
        "comment" : "optional comment",
        "rule" : "stellar expression",
        "score" : numeric score
     }
]
```
   
#### Migration

For every sensor enrichment configuration, you will need to migrate the `riskLevelRules` section
to move from a map to a list of risk level rule objects.

### [METRON-283: Migrate Geo Enrichment outside of MySQL](https://issues.apache.org/jira/browse/METRON-283)

#### Description

As of 0.3.0, a MySQL database was used for storage and retrieval of
GeoIP information during enrichment.
As of 0.3.1, the MySQL database is removed in favor of using MaxMind's
binary GeoIP files and stored on HDFS

After initial setup, this change is transparent and existing enrichment
definitions will run as-is.

#### Migration

While new installs will not require any additional steps, in an existing
install a script must be run to retrieve and load the initial data.

The shell script `geo_enrichment_load.sh` will retrieve MaxMind GeoLite2
data and load data into HDFS, and update the configuration to point to
this data.
In most cases the following usage will grab the data appropriately:

```
$METRON_HOME/bin/geo_enrichment_load.sh -z <zk_server>:<zk_port>
```

Additional options, including changing the source file location (which
can be a file:// location if the GeoIP data is already downloaded), are
available with the
-h flag and are also detailed in the metron-data-management README.me
 file.

One caveat is that this script will NOT update on disk config files. It
is recommended to retrieve the configuration using

```
$METRON_HOME/bin/zk_load_configs.sh -z <zk_server>:<zk_port> -m DUMP
```

The new config will be `geo.hdfs.file` in the global section of the
configuration. Append this key-value into the global.json in the config
directory. A PUSH is unnecessary

### [METRON-684: Decouple Timestamp calculation from PROFILE_GET](https://issues.apache.org/jira/browse/METRON-684)

#### Description

During 0.3.1 we decoupled specifying durations for calls to the profiler
into a separate function.  The consequence is that existing calls to
`PROFILE_GET` will need to migrate.

#### Migration

Existing calls to `PROFILE_GET` will need to change from `PROFILE_GET('profile', 'entity', duration, 'durationUnits')` to `PROFILE_GET('profile', 'entity', PROFILE_FIXED(duration, 'durationUnits'))`

## 0.2.0BETA to 0.3.0
### [METRON-447: Monit fails to reload when upgrading from 0.2.0BETA to master](https://issues.apache.org/jira/browse/METRON-447)

#### Description

`/etc/monit.d/enrichment-elasticsearch.monit` was renamed to
`/etc/monit.d/indexing-elasticsearch.monit`, however the old file isn't
removed via ansible, which causes the below error during an upgrade:
`Starting monit: /etc/monit.d/enrichment-elasticsearch.monit:18: Service
name conflict, enrichment already defined
'/usr/local/monit/status_enrichment_topology.sh'`

### [METRON-448:Upgrading via Ansible deployment does not add topology.classpath ](https://issues.apache.org/jira/browse/METRON-448)

#### Description
When using Ansible to deploy the latest Metron bits to an existing installation, storm-site is not being updated with the new 0.2.1BETA parameter `topology.classpath`. Topologies are unable to find the client configs as a result.

#### Workaround
Set the `topology.classpath` property for storm in Ambari to `/etc/hbase/conf:/etc/hadoop/conf`
