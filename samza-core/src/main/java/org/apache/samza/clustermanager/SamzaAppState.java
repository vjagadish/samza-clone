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
import org.apache.samza.coordinator.JobModelReader;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SamzaAppState encapsulates state like - completedContainers, runningContainers. This
 * class is also used to display information in the Samza UI.
 *
 * TODO:
 * 1.Make these variables private, final
 * 2.Provide thread-safe accessors.
 * //Since the scope of that change is larger, I'm tracking it to work later as a part of SAMZA-902
 *
 */

public class SamzaAppState {

  public enum SamzaAppStatus {UNDEFINED,SUCCEEDED,FAILED}

  public final JobModelReader jobModelReader;

  /**
   * JMX Server URL, if enabled
   * Used for displaying in the AM UI. See scalate/WEB-INF/views/index.scaml
   */
  public String jmxUrl = "";
  /**
   * JMX Server Tunneling URL, if enabled
   * Used for displaying in the AM UI. See scalate/WEB-INF/views/index.scaml
   */
  public String jmxTunnelingUrl = "";
  /**
   * The following state variables are required for the correct functioning of the TaskManager
   * Some of them are shared between the AMRMCallbackThread and the ContainerAllocator thread, as mentioned below.
   */

  /**
   * Number of containers that have completed their execution and exited successfully
   */
  public final AtomicInteger completedContainers = new AtomicInteger(0);

  /**
   * Number of failed containers
   * */
  public final AtomicInteger failedContainers = new AtomicInteger(0);

  /**
   * Number of containers released due to extra allocation returned by the RM
   */
  public final AtomicInteger releasedContainers = new AtomicInteger(0);

  /**
   * ContainerStatus of failed containers.
   */
  public final ConcurrentMap<String, SamzaResourceStatus> failedContainersStatus = new ConcurrentHashMap<String, SamzaResourceStatus>();

  /**
   * Number of containers configured for the job
   */
  public final AtomicInteger containerCount = new AtomicInteger(0);

  /**
   * Set of finished containers - TODO: Can be changed to a counter
   */
  public final Set<Integer> finishedContainers = new HashSet<Integer>();

  /**
   *  Number of containers needed for the job to be declared healthy
   *  Modified by both the AMRMCallbackThread and the ContainerAllocator thread
   */
  public final AtomicInteger neededContainers = new AtomicInteger(0);

  /**
   *  Map of the samzaContainerId to the {@link SamzaResource} on which it is running
   *  Modified by both the AMRMCallbackThread and the ContainerAllocator thread
   */
  public final ConcurrentMap<Integer, SamzaResource> runningContainers = new ConcurrentHashMap<Integer, SamzaResource>(0);

  /**
   * Final status of the application
   */
  public SamzaAppStatus status = SamzaAppStatus.UNDEFINED;

  /**
   * State indicating whether the job is healthy or not
   * Modified by both the callback handler and the ContainerAllocator thread
   */
  public final AtomicBoolean jobHealthy = new AtomicBoolean(true);

  public final AtomicInteger containerRequests = new AtomicInteger(0);

  public final AtomicInteger matchedContainerRequests = new AtomicInteger(0);

  public SamzaAppState(JobModelReader jobModelReader) {
    this.jobModelReader = jobModelReader;
  }
}
