#!/usr/bin/env python
"""
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
"""

import os
import time

from resource_management.core.exceptions import Fail
from resource_management.core.logger import Logger
from resource_management.core.resources.system import Execute

import metron_service
from metron_security import kinit


# Wrap major operations and functionality in this class
class EnrichmentCommands:
    __params = None
    __enrichment_topology = None
    __enrichment_topic = None
    __kafka_configured = False
    __kafka_acl_configured = False
    __hbase_configured = False
    __hbase_acl_configured = False
    __geo_configured = False

    def __init__(self, params):
        if params is None:
            raise ValueError("params argument is required for initialization")
        self.__params = params
        self.__enrichment_topology = params.metron_enrichment_topology
        self.__enrichment_topic = params.enrichment_input_topic
        self.__kafka_configured = os.path.isfile(self.__params.enrichment_kafka_configured_flag_file)
        self.__kafka_acl_configured = os.path.isfile(self.__params.enrichment_kafka_acl_configured_flag_file)
        self.__hbase_configured = os.path.isfile(self.__params.enrichment_hbase_configured_flag_file)
        self.__hbase_acl_configured = os.path.isfile(self.__params.enrichment_hbase_acl_configured_flag_file)
        self.__geo_configured = os.path.isfile(self.__params.enrichment_geo_configured_flag_file)

    def __get_topics(self):
        return [self.__enrichment_topic, self.__params.enrichment_error_topic]

    def __get_kafka_acl_groups(self):
        return [self.__enrichment_topic]

    def is_kafka_configured(self):
        return self.__kafka_configured

    def is_kafka_acl_configured(self):
        return self.__kafka_acl_configured

    def is_hbase_configured(self):
        return self.__hbase_configured

    def is_hbase_acl_configured(self):
        return self.__hbase_acl_configured

    def is_geo_configured(self):
        return self.__geo_configured

    def set_kafka_configured(self):
        metron_service.set_configured(self.__params.metron_user, self.__params.enrichment_kafka_configured_flag_file, "Setting Kafka configured to True for enrichment")

    def set_kafka_acl_configured(self):
        metron_service.set_configured(self.__params.metron_user, self.__params.enrichment_kafka_acl_configured_flag_file, "Setting Kafka ACL configured to True for enrichment")

    def set_hbase_configured(self):
        metron_service.set_configured(self.__params.metron_user, self.__params.enrichment_hbase_configured_flag_file, "Setting HBase configured to True for enrichment")

    def set_hbase_acl_configured(self):
        metron_service.set_configured(self.__params.metron_user, self.__params.enrichment_hbase_acl_configured_flag_file, "Setting HBase ACL configured to True for enrichment")

    def set_geo_configured(self):
        metron_service.set_configured(self.__params.metron_user, self.__params.enrichment_geo_configured_flag_file, "Setting GEO configured to True for enrichment")

    def init_geo(self):
        Logger.info("Creating HDFS location for GeoIP database")
        self.__params.HdfsResource(self.__params.geoip_hdfs_dir,
                                   type="directory",
                                   action="create_on_execute",
                                   owner=self.__params.metron_user,
                                   group=self.__params.metron_group,
                                   mode=0755,
                                   )

        Logger.info("Creating and loading GeoIp database")
        command_template = """{0}/bin/geo_enrichment_load.sh \
                                -g {1} \
                                -r {2} \
                                -z {3}"""
        command = command_template.format(self.__params.metron_home,
                                          self.__params.geoip_url,
                                          self.__params.geoip_hdfs_dir,
                                          self.__params.zookeeper_quorum
                                          )
        Logger.info("Executing command " + command)
        Execute(command, user=self.__params.metron_user, tries=1, logoutput=True)
        Logger.info("Done intializing GeoIP data")
        self.set_geo_configured()

    def init_kafka_topics(self):
        Logger.info('Creating Kafka topics for enrichment')
        # All errors go to indexing topics, so create it here if it's not already
        metron_service.init_kafka_topics(self.__params, self.__get_topics())
        self.set_kafka_configured()

    def init_kafka_acls(self):
        Logger.info('Creating Kafka ACls for enrichment')
        metron_service.init_kafka_acls(self.__params, self.__get_topics())

        # Enrichment topic names matches group
        metron_service.init_kafka_acl_groups(self.__params, self.__get_kafka_acl_groups())

        self.set_kafka_acl_configured()

    def start_enrichment_topology(self, env):
        Logger.info("Starting Metron enrichment topology: {0}".format(self.__enrichment_topology))

        if not self.is_topology_active(env):
            start_cmd_template = """{0}/bin/start_enrichment_topology.sh \
                                        -s {1} \
                                        -z {2}"""
            Logger.info('Starting ' + self.__enrichment_topology)
            start_cmd = start_cmd_template.format(self.__params.metron_home,
                                                  self.__enrichment_topology,
                                                  self.__params.zookeeper_quorum)
            Execute(start_cmd, user=self.__params.metron_user, tries=3, try_sleep=5, logoutput=True)
        else:
            Logger.info('Enrichment topology already running')

        Logger.info('Finished starting enrichment topology')

    def stop_enrichment_topology(self, env):
        Logger.info('Stopping ' + self.__enrichment_topology)

        if self.is_topology_active(env):
            stop_cmd = 'storm kill ' + self.__enrichment_topology
            Execute(stop_cmd, user=self.__params.metron_user, tries=3, try_sleep=5, logoutput=True)
        else:
            Logger.info("Enrichment topology already stopped")

        Logger.info('Done stopping enrichment topologies')

    def restart_enrichment_topology(self, env):
        Logger.info('Restarting the enrichment topologies')
        self.stop_enrichment_topology(env)

        # Wait for old topology to be cleaned up by Storm, before starting again.
        retries = 0
        topology_active = self.is_topology_active(env)
        while topology_active and retries < 3:
            Logger.info('Existing topology still active. Will wait and retry')
            time.sleep(40)
            topology_active = self.is_topology_active(env)
            retries += 1

        if not topology_active:
            self.start_enrichment_topology(env)
            Logger.info('Done restarting the enrichment topology')
        else:
            Logger.warning('Retries exhausted. Existing topology not cleaned up.  Aborting topology start.')

    def is_topology_active(self, env):
        env.set_params(self.__params)

        active = True
        topologies = metron_service.get_running_topologies(self.__params)
        is_running = False
        if self.__enrichment_topology in topologies:
            is_running = topologies[self.__enrichment_topology] in ['ACTIVE', 'REBALANCING']
        active &= is_running
        return active

    def create_hbase_tables(self):
        Logger.info("Creating HBase Tables")
        metron_service.create_hbase_table(self.__params,
                                        self.__params.enrichment_hbase_table,
                                        self.__params.enrichment_hbase_cf)
        metron_service.create_hbase_table(self.__params,
                                        self.__params.threatintel_hbase_table,
                                        self.__params.threatintel_hbase_cf)
        Logger.info("Done creating HBase Tables")
        self.set_hbase_configured()

    def set_hbase_acls(self):
        Logger.info("Setting HBase ACLs")
        if self.__params.security_enabled:
            kinit(self.__params.kinit_path_local,
                  self.__params.hbase_keytab_path,
                  self.__params.hbase_principal_name,
                  execute_user=self.__params.hbase_user)

        cmd = "echo \"grant '{0}', 'RW', '{1}'\" | hbase shell -n"
        add_enrichment_acl_cmd = cmd.format(self.__params.metron_user, self.__params.enrichment_hbase_table)
        Execute(add_enrichment_acl_cmd,
                tries=3,
                try_sleep=5,
                logoutput=False,
                path='/usr/sbin:/sbin:/usr/local/bin:/bin:/usr/bin',
                user=self.__params.hbase_user
                )

        add_threatintel_acl_cmd = cmd.format(self.__params.metron_user, self.__params.threatintel_hbase_table)
        Execute(add_threatintel_acl_cmd,
                tries=3,
                try_sleep=5,
                logoutput=False,
                path='/usr/sbin:/sbin:/usr/local/bin:/bin:/usr/bin',
                user=self.__params.hbase_user
                )

        Logger.info("Done setting HBase ACLs")
        self.set_hbase_acl_configured()

    def service_check(self, env):
        """
        Performs a service check for Enrichment.
        :param env: Environment
        """
        Logger.info("Checking for Geo database")
        metron_service.check_hdfs_file_exists(self.__params, self.__params.geoip_hdfs_dir + "/GeoLite2-City.mmdb.gz")

        Logger.info('Checking Kafka topics for Enrichment')
        metron_service.check_kafka_topics(self.__params, self.__get_topics())

        Logger.info("Checking HBase for Enrichment")
        metron_service.check_hbase_table(
          self.__params,
          self.__params.enrichment_hbase_table)
        metron_service.check_hbase_column_family(
          self.__params,
          self.__params.enrichment_hbase_table,
          self.__params.enrichment_hbase_cf)

        Logger.info("Checking HBase for Threat Intel")
        metron_service.check_hbase_table(
          self.__params,
          self.__params.threatintel_hbase_table)
        metron_service.check_hbase_column_family(
          self.__params,
          self.__params.threatintel_hbase_table,
          self.__params.threatintel_hbase_cf)

        if self.__params.security_enabled:

          Logger.info('Checking Kafka ACLs for Enrichment')
          metron_service.check_kafka_acls(self.__params, self.__get_topics())
          metron_service.check_kafka_acl_groups(self.__params, self.__get_kafka_acl_groups())

          Logger.info("Checking HBase ACLs for Enrichment")
          metron_service.check_hbase_acls(self.__params, self.__params.enrichment_hbase_table)
          metron_service.check_hbase_acls(self.__params, self.__params.threatintel_hbase_table)

        Logger.info("Checking for Enrichment topology")
        if not self.is_topology_active(env):
            raise Fail("Enrichment topology not running")

        Logger.info("Enrichment service check completed successfully")
