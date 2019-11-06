/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.ethereum.beacon.discovery.task;

import com.google.common.collect.Sets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoveryManager;
import org.ethereum.beacon.discovery.scheduler.ExpirationScheduler;
import org.ethereum.beacon.discovery.scheduler.Scheduler;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/**
 * Sends {@link TaskType#PING} to closest NodeRecords added via {@link #add(NodeRecordInfo,
 * Runnable, Runnable)}. Tasks is called failed if timeout is reached and reply from node is not
 * received.
 */
public class LiveCheckTasks {
  private final Scheduler scheduler;
  private final DiscoveryManager discoveryManager;
  private final Set<Bytes> currentTasks = Sets.newConcurrentHashSet();
  private final ExpirationScheduler<Bytes> taskTimeouts;

  public LiveCheckTasks(DiscoveryManager discoveryManager, Scheduler scheduler, Duration timeout) {
    this.discoveryManager = discoveryManager;
    this.scheduler = scheduler;
    this.taskTimeouts =
        new ExpirationScheduler<>(timeout.get(ChronoUnit.MILLIS), TimeUnit.MILLISECONDS);
  }

  public void add(NodeRecordInfo nodeRecordInfo, Runnable successCallback, Runnable failCallback) {
    synchronized (this) {
      if (currentTasks.contains(nodeRecordInfo.getNode().getNodeId())) {
        return;
      }
      currentTasks.add(nodeRecordInfo.getNode().getNodeId());
    }

    scheduler.execute(
        () -> {
          CompletableFuture<Void> retry =
              discoveryManager.executeTask(nodeRecordInfo.getNode(), TaskType.PING);
          taskTimeouts.put(
              nodeRecordInfo.getNode().getNodeId(),
              () ->
                  retry.completeExceptionally(new RuntimeException("Timeout for node check task")));
          retry.whenComplete(
              (aVoid, throwable) -> {
                if (throwable != null) {
                  failCallback.run();
                  currentTasks.remove(nodeRecordInfo.getNode().getNodeId());
                } else {
                  successCallback.run();
                  currentTasks.remove(nodeRecordInfo.getNode().getNodeId());
                }
              });
        });
  }
}
