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
package org.apache.samza.clustermanager;

import org.apache.samza.config.Config;

import java.lang.reflect.Field;

import java.util.Map;

public class MockContainerAllocator extends ContainerAllocator {
  public int requestedContainers = 0;

  public MockContainerAllocator(ClusterResourceManager manager,
                                Config config,
                                SamzaApplicationState state) {
    super(manager, config, state);
  }

  @Override
  public void requestResources(Map<Integer, String> containerToHostMappings) {
    requestedContainers += containerToHostMappings.size();
    super.requestResources(containerToHostMappings);
  }

  public ResourceRequestState getContainerRequestState() throws Exception {
    Field field = AbstractContainerAllocator.class.getDeclaredField("resourceRequestState");
    field.setAccessible(true);

    return (ResourceRequestState) field.get(this);
  }
}
