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

import org.apache.samza.SamzaException;
import org.apache.samza.config.ClusterManagerConfig;
import org.apache.samza.config.Config;
import org.apache.samza.config.JobConfig;
import org.apache.samza.config.TaskConfig;
import org.apache.samza.job.CommandBuilder;
import org.apache.samza.job.ShellCommandBuilder;
import org.apache.samza.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * {@link AbstractContainerAllocator} makes requests for physical resources to the resource manager and also, assigning
 * a container to run on an allocated resource. Sub-classes should override the allocateContainers() method to match
 * containers to hosts according to some strategy.
 *
 * See {@link ContainerAllocator} and {@link HostAwareContainerAllocator} for two such strategies
 *
 * It is not safe to share the same object among multiple threads without external synchronization.
 */
public abstract class AbstractContainerAllocator implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(AbstractContainerAllocator.class);
  /**
   * A ContainerProcessManager for the allocator to request for resources.
   */
  protected final ContainerProcessManager containerProcessManager;
  /**
   * The allocator sleeps for ALLOCATOR_SLEEP_TIME before it polls its queue for the next request
   */
  protected final int ALLOCATOR_SLEEP_TIME;
  /**
   * Each container currently has the same configuration - memory, and numCpuCores.
   * Hence, those can be configured in the allocator.
   */
  protected final int containerMemoryMb;
  protected final int containerNumCpuCore;
  /**
   * Config and derived config objects
   */
  private final TaskConfig taskConfig;

  private Config config;
  /**
   * State corresponding to num failed containers, running containers etc.
   */
  SamzaAppState state;

  /**
   * ContainerRequestState indicate the state of all unfulfilled container requests and allocated containers
   */
  protected final ContainerRequestState containerRequestState;

  /* State that controls the lifecycle of the allocator thread*/
  private AtomicBoolean isRunning = new AtomicBoolean(true);

  public AbstractContainerAllocator(ContainerProcessManager containerProcessManager,
                                    ContainerRequestState containerRequestState,
                                    Config config, SamzaAppState state) {
    ClusterManagerConfig clusterManagerConfig = new ClusterManagerConfig(config);
    this.containerProcessManager = containerProcessManager;
    this.ALLOCATOR_SLEEP_TIME = clusterManagerConfig.getAllocatorSleepTime();
    this.containerRequestState = containerRequestState;
    this.containerMemoryMb = clusterManagerConfig.getContainerMemoryMb();
    this.containerNumCpuCore = clusterManagerConfig.getNumCores();
    this.taskConfig = new TaskConfig(config);
    this.state = state;
    this.config = config;
  }

  /**
   * Continuously assigns requested containers to the allocated containers provided by the cluster manager.
   * The loop frequency is governed by thread sleeps for ALLOCATOR_SLEEP_TIME ms.
   *
   * Terminates when the isRunning flag is cleared.
   */
  @Override
  public void run() {
    while(isRunning.get()) {
      try {
        assignContainerRequests();
        // Release extra containers and update the entire system's state
        containerRequestState.releaseExtraContainers();
        Thread.sleep(ALLOCATOR_SLEEP_TIME);
      }
      catch (InterruptedException e) {
        //TODO: better interrupt handling.
        log.info("Got InterruptedException in AllocatorThread.", e);
      }
      catch (Exception e) {
        log.error("Got unknown Exception in AllocatorThread.", e);
      }
    }
  }

  /**
   * Assigns the container requests from the queue to the allocated containers from the cluster manager and
   * runs them.
   */
  protected abstract void assignContainerRequests();

  /**
   * Updates the request state and runs the container on the specified host. Assumes a container
   * is available on the preferred host, so the caller must verify that before invoking this method.
   *
   * @param request             the {@link SamzaResourceRequest} which is being handled.
   * @param preferredHost       the preferred host on which the container should be run or
   *                            {@link ContainerRequestState#ANY_HOST} if there is no host preference.
   * @throws
   * SamzaException if there is no available container in the specified host.
   */
  protected void runStreamProcessor(SamzaResourceRequest request, String preferredHost) {
    CommandBuilder builder = getCommandBuilder(request.getExpectedContainerID());
    // Get the available resource
    SamzaResource resource = peekAllocatedContainer(preferredHost);
    if (resource == null)
      throw new SamzaException("Expected resource was unavailable on host " + preferredHost);

    // Update state
    containerRequestState.updateStateAfterAssignment(request, preferredHost, resource);
    int expectedContainerId = request.getExpectedContainerID();

    // Cancel request and run resource
    log.info("Found available containers on {}. Assigning request for container_id {} with "
            + "timestamp {} to resource {}",
        new Object[]{preferredHost, String.valueOf(expectedContainerId), request.getRequestTimestampMs(), resource.getResourceID()});
    try {
      //launches a StreamProcessor on the resource
      containerProcessManager.launchStreamProcessor(resource, builder);

      if (state.neededContainers.decrementAndGet() == 0) {
        state.jobHealthy.set(true);
      }
      state.runningContainers.put(request.getExpectedContainerID(), resource);

    } catch (SamzaContainerLaunchException e) {
      log.warn(String.format("Got exception while starting resource %s. Requesting a new resource on any host", resource), e);
      //containerRequestState.releaseUnstartableContainer(resource);
      containerProcessManager.releaseResources(resource);
      requestContainer(expectedContainerId, ContainerRequestState.ANY_HOST);
    }
  }

  /**
   * Called during initial request for containers
   *
   * @param containerToHostMappings Map of containerId to its last seen host (locality).
   *                                The locality value is null, either
   *                                - when host-affinity is not enabled, or
   *                                - when host-affinity is enabled and job is run for the first time
   */
  public void requestContainers(Map<Integer, String> containerToHostMappings) {
    for (Map.Entry<Integer, String> entry : containerToHostMappings.entrySet()) {
      int containerId = entry.getKey();
      String preferredHost = entry.getValue();
      if (preferredHost == null)
        preferredHost = ContainerRequestState.ANY_HOST;

      requestContainer(containerId, preferredHost);
    }
  }

  /**
   * @return {@code true} if there is a pending request, {@code false} otherwise.
   */
  protected boolean hasPendingRequest() {
    return peekPendingRequest() != null;
  }

  /**
   * Retrieves, but does not remove, the next pending request in the queue.
   *
   * @return  the pending request or {@code null} if there is no pending request.
   */
  protected SamzaResourceRequest peekPendingRequest() {
    return containerRequestState.getRequestsQueue().peek();
  }

  /**
   * Method to request a container resource from yarn
   *
   * @param expectedContainerId Identifier of the container that will be run when a container resource is allocated for
   *                            this request
   * @param preferredHost Name of the host that you prefer to run the container on
   */
  public final void requestContainer(int expectedContainerId, String preferredHost) {
    SamzaResourceRequest request = new SamzaResourceRequest(this.containerNumCpuCore, this.containerMemoryMb,
        preferredHost, UUID.randomUUID().toString() ,expectedContainerId);
    containerRequestState.addResourceRequest(request);
    state.containerRequests.incrementAndGet();
  }

  /**
   * @param host  the host for which a container is needed.
   * @return      {@code true} if there is a container allocated for the specified host, {@code false} otherwise.
   */
  protected boolean hasAllocatedContainer(String host) {
    return peekAllocatedContainer(host) != null;
  }

  /**
   * Retrieves, but does not remove, the first allocated container on the specified host.
   *
   * @param host  the host for which a container is needed.
   * @return      the first {@link SamzaResource} allocated for the specified host or {@code null} if there isn't one.
   */
  protected SamzaResource peekAllocatedContainer(String host) {
    List<SamzaResource> allocatedContainers = containerRequestState.getContainersOnAHost(host);
    if (allocatedContainers == null || allocatedContainers.isEmpty()) {
      return null;
    }

    return allocatedContainers.get(0);
  }

  /**
   * Returns a command builder with the build environment configured with the containerId.
   * @param samzaContainerId to configure the builder with.
   * @return
   */
  private CommandBuilder getCommandBuilder(int samzaContainerId) {
    String cmdBuilderClassName = taskConfig.getCommandClass(ShellCommandBuilder.class.getName());
    CommandBuilder cmdBuilder = (CommandBuilder) Util.getObj(cmdBuilderClassName);
    cmdBuilder.setConfig(config).setId(samzaContainerId).setUrl(state.jobModelReader.server().getUrl());
    return cmdBuilder;
  }
  /**
   * Adds allocated container to a synchronized buffer of allocated containers list
   * See allocatedContainers in {@link ContainerRequestState}
   *
   * @param container Container resource returned by the RM
   */
  public final void addContainer(SamzaResource container) {
    containerRequestState.addResource(container);
  }


  public void stop() {
    isRunning.set(false);
  }

}