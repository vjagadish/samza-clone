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
package org.apache.samza.job.yarn.util;

import java.lang.reflect.Field;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.async.AMRMClientAsync;
import org.apache.samza.config.YarnConfig;
import org.apache.samza.job.yarn.AbstractContainerAllocator;
import org.apache.samza.job.yarn.ContainerAllocator;
import org.apache.samza.job.yarn.ContainerRequestState;
import org.apache.samza.job.yarn.ContainerUtil;

import java.util.Map;

public class MockContainerAllocator extends ContainerAllocator {
  public int requestedContainers = 0;

  public MockContainerAllocator(AMRMClientAsync<AMRMClient.ContainerRequest> amrmClientAsync,
                                ContainerUtil containerUtil,
                                YarnConfig yarnConfig) {
    super(amrmClientAsync, containerUtil, yarnConfig);
  }

  @Override
  public void requestContainers(Map<Integer, String> containerToHostMappings) {
    requestedContainers += containerToHostMappings.size();
    super.requestContainers(containerToHostMappings);
  }

  public ContainerRequestState getContainerRequestState() throws Exception {
    Field field = AbstractContainerAllocator.class.getDeclaredField("containerRequestState");
    field.setAccessible(true);

    return (ContainerRequestState) field.get(this);
  }
}
