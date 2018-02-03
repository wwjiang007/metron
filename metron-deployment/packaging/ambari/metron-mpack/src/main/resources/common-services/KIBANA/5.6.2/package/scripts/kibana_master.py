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

import errno
import os

from ambari_commons.os_check import OSCheck
from ambari_commons.os_family_impl import OsFamilyFuncImpl, OsFamilyImpl

from resource_management.core.logger import Logger
from resource_management.core.resources.system import Directory
from resource_management.core.resources.system import Execute
from resource_management.core.resources.system import File
from resource_management.core.source import InlineTemplate
from resource_management.libraries.functions.format import format as ambari_format
from resource_management.libraries.script import Script

from common import service_check

class Kibana(Script):

    def install(self, env):
        import params
        env.set_params(params)
        Logger.info("Installing Kibana")
        self.install_packages(env)

    def configure(self, env, upgrade_type=None, config_dir=None):
        import params
        env.set_params(params)
        Logger.info("Configuring Kibana")

        directories = [params.log_dir, params.pid_dir, params.conf_dir]
        Directory(directories,
                  mode=0755,
                  owner=params.kibana_user,
                  group=params.kibana_user
                  )

        File("{0}/kibana.yml".format(params.conf_dir),
             owner=params.kibana_user,
             content=InlineTemplate(params.kibana_yml_template)
             )

    def stop(self, env, upgrade_type=None):
        import params
        env.set_params(params)
        Logger.info("Stopping Kibana")
        Execute("service kibana stop")

    def start(self, env, upgrade_type=None):
        import params
        env.set_params(params)
        self.configure(env)
        Logger.info("Starting Kibana")
        Execute("service kibana start")

    def restart(self, env):
        import params
        env.set_params(params)
        self.configure(env)
        Logger.info("Restarting Kibana")
        Execute("service kibana restart")

    def status(self, env):
        import params
        env.set_params(params)
        Logger.info('Status check Kibana')
        service_check("service kibana status", user=params.kibana_user, label="Kibana")

    @OsFamilyFuncImpl(os_family=OsFamilyImpl.DEFAULT)
    def load_template(self, env):
        import params
        env.set_params(params)

        hostname = ambari_format("{es_host}")
        port = int(ambari_format("{es_port}"))

        Logger.info("Connecting to Elasticsearch on host: %s, port: %s" % (hostname, port))

        kibanaTemplate = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dashboard', 'kibana.template')
        if not os.path.isfile(kibanaTemplate):
          raise IOError(
              errno.ENOENT, os.strerror(errno.ENOENT), kibanaTemplate)

        Logger.info("Loading .kibana index template from %s" % kibanaTemplate)
        template_cmd = ambari_format(
            'curl -s -XPOST http://{es_host}:{es_port}/_template/.kibana -d @%s' % kibanaTemplate)
        Execute(template_cmd, logoutput=True)

        kibanaDashboardLoad = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'dashboard', 'dashboard-bulkload.json')
        if not os.path.isfile(kibanaDashboardLoad):
          raise IOError(
              errno.ENOENT, os.strerror(errno.ENOENT), kibanaDashboardLoad)

        Logger.info("Loading .kibana dashboard from %s" % kibanaDashboardLoad)

        kibana_cmd = ambari_format(
            'curl -s -H "Content-Type: application/x-ndjson" -XPOST http://{es_host}:{es_port}/.kibana/_bulk --data-binary @%s' % kibanaDashboardLoad)
        Execute(kibana_cmd, logoutput=True)


if __name__ == "__main__":
    Kibana().execute()
