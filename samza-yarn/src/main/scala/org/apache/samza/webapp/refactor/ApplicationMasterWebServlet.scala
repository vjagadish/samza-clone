/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.samza.webapp.refactor

import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.hadoop.yarn.webapp.util.WebAppUtils
import org.apache.samza.config.Config
import org.apache.samza.job.yarn.SamzaAppState
import org.apache.samza.job.yarn.refactor.YarnAppState
import org.scalatra._
import org.scalatra.scalate.ScalateSupport

import scala.collection.JavaConversions._
import scala.collection.immutable.TreeMap

class ApplicationMasterWebServlet(config: Config, state: YarnAppState) extends ScalatraServlet with ScalateSupport {
  val yarnConfig = new YarnConfiguration

  before() {
    contentType = "text/html"
  }

  get("/") {
    layoutTemplate("/WEB-INF/views/index.scaml",
      "config" -> TreeMap(config.sanitize.toMap.toArray: _*),
      "state" -> state,
      "rmHttpAddress" -> WebAppUtils.getRMWebAppURLWithScheme(yarnConfig))
  }
}
