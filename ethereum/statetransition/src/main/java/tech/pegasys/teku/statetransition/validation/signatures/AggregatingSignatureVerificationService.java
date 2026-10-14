/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.statetransition.validation.signatures;

import static java.util.Collections.singletonList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.MetricsQuantileHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.service.serviceutils.ServiceCapacityExceededException;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

public class AggregatingSignatureVerificationService extends SignatureVerificationService {
  private static final Logger LOG = LogManager.getLogger();

  static final int DEFAULT_MIN_BATCH_SIZE_TO_SPLIT = 25;

  private static final Duration IDLE_POLL_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration GATHER_LOCK_TIMEOUT = Duration.ofSeconds(1);

  private final AsyncRunner completionRunner;
  private final int numThreads;
  private final int maxBatchSize;
  private final int minBatchSizeToSplit;
  private final boolean strictThreadLimitEnabled;
  private final long batchGatherTimeoutNanos;
  private final int batchGatherTargetSize;

  @VisibleForTesting final BlockingQueue<SignatureTask> batchSignatureTasks;
  // Only one worker gathers a batch at a time, otherwise tasks arriving during the gather window
  // would be spread one-by-one across all idle workers instead of forming a single batch
  private final ReentrantLock gatherLock = new ReentrantLock();
  private final AsyncRunner asyncRunner;
  private final Counter batchCounter;
  private final Counter taskCounter;
  private final Counter urgentTaskCounter;
  private final MetricsQuantileHistogram batchSizeHistogram;
  private final MetricsQuantileHistogram batchGatherTimeHistogram;
  private final AsyncBLSSignatureVerifier urgentView = new UrgentView();

  @VisibleForTesting
  AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner completionRunner,
      final int numThreads,
      final int queueCapacity,
      final int maxBatchSize,
      final int minBatchSizeToSplit,
      final boolean strictThreadLimitEnabled,
      final Duration batchGatherTimeout,
      final int batchGatherTargetSize) {
    this.numThreads = Math.min(numThreads, Runtime.getRuntime().availableProcessors());
    this.asyncRunner = asyncRunnerFactory.create(this.getClass().getSimpleName(), this.numThreads);
    this.completionRunner = completionRunner;
    this.maxBatchSize = maxBatchSize;

    this.batchSignatureTasks = new ArrayBlockingQueue<>(queueCapacity);
    this.minBatchSizeToSplit = minBatchSizeToSplit;
    this.strictThreadLimitEnabled = strictThreadLimitEnabled;
    this.batchGatherTimeoutNanos = batchGatherTimeout.toNanos();
    this.batchGatherTargetSize = batchGatherTargetSize;
    metricsSystem.createGauge(
        TekuMetricCategory.EXECUTOR,
        "signature_verifications_queue_size",
        "Tracks number of signatures waiting to be batch verified",
        this::getQueueSize);
    batchCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.EXECUTOR,
            "signature_verifications_batch_count_total",
            "Reports the number of verification batches processed");
    taskCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.EXECUTOR,
            "signature_verifications_task_count_total",
            "Reports the number of individual verification tasks processed");
    urgentTaskCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.EXECUTOR,
            "signature_verifications_urgent_task_count_total",
            "Reports the number of verification tasks submitted as urgent (not held back while"
                + " gathering a batch)");
    batchSizeHistogram =
        MetricsQuantileHistogram.create(
            TekuMetricCategory.EXECUTOR,
            metricsSystem,
            "signature_verifications_batch_size",
            "Histogram of signature verification batch sizes",
            3,
            List.of());
    batchGatherTimeHistogram =
        MetricsQuantileHistogram.create(
            TekuMetricCategory.EXECUTOR,
            metricsSystem,
            "signature_verifications_batch_gather_time_micros",
            "Histogram of the time spent gathering a batch after its first task was taken, in"
                + " microseconds",
            3,
            List.of());
  }

  public AggregatingSignatureVerificationService(
      final MetricsSystem metricsSystem,
      final AsyncRunnerFactory asyncRunnerFactory,
      final AsyncRunner completionRunner,
      final int maxThreads,
      final int queueCapacity,
      final int maxBatchSize,
      final boolean strictThreadLimitEnabled,
      final Duration batchGatherTimeout,
      final int batchGatherTargetSize) {
    this(
        metricsSystem,
        asyncRunnerFactory,
        completionRunner,
        maxThreads,
        queueCapacity,
        maxBatchSize,
        DEFAULT_MIN_BATCH_SIZE_TO_SPLIT,
        strictThreadLimitEnabled,
        batchGatherTimeout,
        batchGatherTargetSize);
  }

  @Override
  protected SafeFuture<?> doStart() {
    for (int i = 0; i < numThreads; i++) {
      asyncRunner
          .runAsync(this::run)
          .finish(
              err ->
                  AggregatingSignatureVerificationService.LOG.error(
                      "Signature Verification Task failed", err));
    }

    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    return verify(singletonList(publicKeys), singletonList(message), singletonList(signature));
  }

  @Override
  public SafeFuture<Boolean> verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    return submit(publicKeys, messages, signatures, false);
  }

  @Override
  public AsyncBLSSignatureVerifier urgent() {
    return urgentView;
  }

  private SafeFuture<Boolean> submit(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures,
      final boolean urgent) {
    assertIsRunning("verify");
    if (urgent) {
      urgentTaskCounter.inc();
    }
    final SignatureTask task =
        new SignatureTask(completionRunner, publicKeys, messages, signatures, urgent);
    if (!batchSignatureTasks.offer(task)) {
      // Queue is full
      final Throwable error =
          new ServiceCapacityExceededException("Failed to process signature, queue is full.");
      task.result.completeExceptionally(error);
    }
    return task.result;
  }

  private void run() {
    while (isRunning()) {
      final List<SignatureTask> tasks = waitForBatch();
      if (!tasks.isEmpty()) {
        try {
          batchVerifySignatures(tasks);
        } catch (final RuntimeException ex) {
          // Batch verification can throw on malformed input (for example a BLS aggregate public
          // key at infinity). Keep the worker alive by falling back to verifying the tasks
          // individually rather than letting the exception unwind the loop and drop the thread.
          LOG.error("Unexpected error during batch signature verification", ex);
          verifyTasksIndividually(tasks);
        }
      }
    }
  }

  @VisibleForTesting
  void verifyTasksIndividually(final List<SignatureTask> tasks) {
    // Urgent tasks are settled first here too, so that the guarantee holds on every failure path
    for (final boolean urgentPass : new boolean[] {true, false}) {
      for (final SignatureTask task : tasks) {
        if (task.urgent != urgentPass || task.result.isDone()) {
          // Not this pass, or already verified before the failure; skip it so we don't repeat the
          // expensive verification or overwrite its result.
          continue;
        }
        try {
          task.completeAsync(verifyIndividually(task));
        } catch (final RuntimeException ex) {
          // Isolate the failure to this task so the rest of the batch still completes.
          task.completeExceptionallyAsync(ex);
        }
      }
    }
  }

  @VisibleForTesting
  List<SignatureTask> waitForBatch() {
    final List<SignatureTask> tasks = new ArrayList<>();
    try {
      if (!gatherLock.tryLock(GATHER_LOCK_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
        // Let the caller re-check whether the service is still running
        return tasks;
      }
      try {
        final SignatureTask firstTask =
            batchSignatureTasks.poll(IDLE_POLL_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        if (firstTask == null) {
          return tasks;
        }
        final long gatherStart = System.nanoTime();
        tasks.add(firstTask);
        batchSignatureTasks.drainTo(tasks, maxBatchSize - tasks.size());
        int urgentScanIndex = 0;

        // Keep gathering for a short while so that a steady trickle of tasks forms a real batch,
        // unless an urgent task is already in hand: those must not be held back
        final long deadline = gatherStart + batchGatherTimeoutNanos;
        while (tasks.size() < batchGatherTargetSize && tasks.size() < maxBatchSize) {
          if (containsUrgentTask(tasks, urgentScanIndex)) {
            break;
          }
          urgentScanIndex = tasks.size();
          final long remainingNanos = deadline - System.nanoTime();
          if (remainingNanos <= 0) {
            break;
          }
          final SignatureTask nextTask =
              batchSignatureTasks.poll(remainingNanos, TimeUnit.NANOSECONDS);
          if (nextTask == null) {
            break;
          }
          tasks.add(nextTask);
          batchSignatureTasks.drainTo(tasks, maxBatchSize - tasks.size());
        }
        batchGatherTimeHistogram.recordValue(
            TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - gatherStart));
      } finally {
        gatherLock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return tasks;
  }

  @VisibleForTesting
  void batchVerifySignatures(final List<SignatureTask> tasks) {
    batchCounter.inc();
    taskCounter.inc(tasks.size());
    batchSizeHistogram.recordValue(tasks.size());
    final List<List<BLSPublicKey>> allKeys = new ArrayList<>();
    final List<Bytes> allMessages = new ArrayList<>();
    final List<BLSSignature> allSignatures = new ArrayList<>();

    for (SignatureTask task : tasks) {
      allKeys.addAll(task.publicKeys);
      allMessages.addAll(task.messages);
      allSignatures.addAll(task.signatures);
    }

    final boolean batchIsValid =
        strictThreadLimitEnabled
            ? BLS.batchVerify(allKeys, allMessages, allSignatures, allKeys.size() > 1, false)
            : BLS.batchVerify(allKeys, allMessages, allSignatures);
    if (batchIsValid) {
      for (SignatureTask task : tasks) {
        task.completeAsync(true);
      }
    } else if (tasks.size() == 1) {
      // We only had 1 signature, so it must be invalid
      tasks.getFirst().completeAsync(false);
    } else {
      // Urgent tasks must not wait behind the subdivision of a failed batch, which can take
      // several rounds when the batch holds invalid signatures: settle them first on their own
      final List<SignatureTask> remainingTasks = verifyUrgentTasksIndividually(tasks);
      if (remainingTasks.size() >= minBatchSizeToSplit) {
        // Split up tasks and try to verify in smaller batches
        final List<List<SignatureTask>> splitTasks = splitTasks(remainingTasks);
        for (List<SignatureTask> splitTask : splitTasks) {
          batchVerifySignatures(splitTask);
        }
      } else {
        // Validate each signature individually
        for (SignatureTask task : remainingTasks) {
          task.completeAsync(verifyIndividually(task));
        }
      }
    }
  }

  /** Verifies and completes the urgent tasks of a failed batch, returning the other tasks. */
  private List<SignatureTask> verifyUrgentTasksIndividually(final List<SignatureTask> tasks) {
    if (tasks.stream().noneMatch(task -> task.urgent)) {
      return tasks;
    }
    final List<SignatureTask> remainingTasks = new ArrayList<>(tasks.size());
    for (final SignatureTask task : tasks) {
      if (task.urgent) {
        task.completeAsync(verifyIndividually(task));
      } else {
        remainingTasks.add(task);
      }
    }
    return remainingTasks;
  }

  private static boolean verifyIndividually(final SignatureTask task) {
    return BLSSignatureVerifier.SIMPLE.verify(task.publicKeys, task.messages, task.signatures);
  }

  @VisibleForTesting
  List<List<SignatureTask>> splitTasks(final List<SignatureTask> tasks) {
    final int splitListSize = Math.toIntExact((long) Math.ceil(tasks.size() / 2.0));
    return Lists.partition(tasks, splitListSize);
  }

  private double getQueueSize() {
    return batchSignatureTasks.size();
  }

  private static boolean containsUrgentTask(final List<SignatureTask> tasks, final int fromIndex) {
    for (int i = fromIndex; i < tasks.size(); i++) {
      if (tasks.get(i).urgent) {
        return true;
      }
    }
    return false;
  }

  /** Submits every verification as urgent. */
  private class UrgentView implements AsyncBLSSignatureVerifier {
    @Override
    public SafeFuture<Boolean> verify(
        final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
      return submit(
          singletonList(publicKeys), singletonList(message), singletonList(signature), true);
    }

    @Override
    public SafeFuture<Boolean> verify(
        final List<List<BLSPublicKey>> publicKeys,
        final List<Bytes> messages,
        final List<BLSSignature> signatures) {
      return submit(publicKeys, messages, signatures, true);
    }
  }

  @VisibleForTesting
  static class SignatureTask {
    final SafeFuture<Boolean> result = new SafeFuture<>();
    private final AsyncRunner asyncRunner;
    final List<List<BLSPublicKey>> publicKeys;
    final List<Bytes> messages;
    final List<BLSSignature> signatures;
    final boolean urgent;

    private SignatureTask(
        final AsyncRunner asyncRunner,
        final List<List<BLSPublicKey>> publicKeys,
        final List<Bytes> messages,
        final List<BLSSignature> signatures,
        final boolean urgent) {
      this.asyncRunner = asyncRunner;
      this.publicKeys = publicKeys;
      this.messages = messages;
      this.signatures = signatures;
      this.urgent = urgent;
    }

    public void completeAsync(final boolean isValid) {
      asyncRunner.runAsync(() -> result.complete(isValid)).finish(result::completeExceptionally);
    }

    public void completeExceptionallyAsync(final Throwable error) {
      asyncRunner
          .runAsync(() -> result.completeExceptionally(error))
          .finish(result::completeExceptionally);
    }
  }
}
