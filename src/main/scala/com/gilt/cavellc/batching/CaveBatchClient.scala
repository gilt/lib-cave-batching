package com.gilt.cavellc.batching

import java.util.{Timer, TimerTask}

import com.gilt.cavellc.models.Metric
import com.gilt.gfc.logging.Loggable
import com.gilt.gfc.concurrent.ScalaFutures._

import scala.collection.mutable
import scala.concurrent.forkjoin.ForkJoinPool
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait MetricsBatchClient {
  /**
   * Create metric
   *
   * @param metric Metrics to send.
   */
  def createMetric(metric: Metric): Unit

  /**
   * Explicitly shutdown the batching client.
   *
   * Specifically, this cancels internal timers, etc.
   *
   * Note, internal timers, etc., will be created as daemons, so should not block normal terminations if this
   * method is not called.
   */
  def shutdown(): Unit
}

/**
 * Simple batching of metrics for CAVE.
 *
 * This class gathers raw metrics together, and sends to CAVE, as batches, in a timely fashion.
 *
 * The approach in this class is to collect metrics into collections, and when full, or a timer (based on the age of the first
 * metric in the collection) expires, that batch is sent to CAVE.
 *
 * A minimal runtime impact is achieved by using a single Timer, and a single thread - the timer is used simply to schedule tasks
 * to be executed on the thread, with the thread being used for all the work (interacting with CAVE, etc.).
 *
 * Note, each instance of this class builds its own batches, and delivers to CAVE individually - multiple instances will result
 * in multiple batches, if this is so desired.
 */
class CaveBatchClient(batchConfig: CaveBatchConfiguration, publishMetrics: Seq[Metric] => Future[Unit]) extends MetricsBatchClient with Loggable {

  private var timerTask: Option[TimerTask] = None
  private val metrics = mutable.ArrayBuffer.empty[Metric]

  /**
    * Mutex to ensure correct handling around the growing collection representing the batch, control of timers,
    * and initiation of delivering the batches, etc.
    * This will not be used to coordinate communication with CAVE, or any other long/blocking operation.
    */
  private val mutex = new Object

  def createMetric(metric: Metric): Unit = {
    mutex.synchronized {
      if (metrics.isEmpty) {
        startBatchTimer()
      }

      metrics += metric

      if (complete) {
        cancelBatchTimer()
        send()
      }
    }
  }

  private def complete: Boolean = metrics.size >= batchConfig.sendBatchSize

  private def send(attemptNumber: Int = 1, sendBatchOpt: Option[List[Metric]] = None): Unit = {
    // re-entrant, but ensure mutual exclusion, as there are multiple paths to call this method
    mutex.synchronized {
      val sendBatch = sendBatchOpt.getOrElse {
        val batch = metrics.toList
        metrics.clear()
        batch
      }

      publishMetrics(sendBatch).withTimeout(batchConfig.publishTimeout, Some("Failed to send metrics batch")).onComplete {
        case Success(_) =>
          debug(s"batch of ${sendBatch.size} metrics successfully sent")

        case Failure(ex) =>
          if (attemptNumber < batchConfig.maxAttempts) {
            warn(s"Failed to send metrics to CAVE, retrying shortly", ex)
            timer.schedule(new FutureLaunchingTimerTask(send(attemptNumber + 1, Some(sendBatch))), batchConfig.retryTimeout.toMillis)
          } else {
            warn(s"Batch of metrics failed to be sent to CAVE $attemptNumber times - dumping ${sendBatch.size} metrics", ex)
          }
      }
    }
  }

  private def cancelBatchTimer(): Unit = mutex.synchronized {
    timerTask.foreach(_.cancel)
    timerTask = None
  }

  private def startBatchTimer(): Unit = {
    val task = new FutureLaunchingTimerTask(
      mutex.synchronized {
        timerTask = None
        send(1)
      }
    )
    timer.schedule(task, batchConfig.batchTimeout.toMillis)
    timerTask = Some(task)
  }

  // Only a single task is allowed to run concurrently
  private implicit val executionContext = ExecutionContext.fromExecutor(new ForkJoinPool(1))

  private val timer = new Timer("cave-batching-timer", true)

  private class FutureLaunchingTimerTask(func: => Unit)(implicit ec: ExecutionContext) extends TimerTask {
    override def run(): Unit = func
  }

  def shutdown(): Unit = {
    timer.cancel()
  }
}
