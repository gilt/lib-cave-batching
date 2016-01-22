package com.gilt.cavellc.batching

import scala.concurrent.duration._

/**
 * Several configurations here around batching have reasonable default values.
 */
trait CaveBatchConfiguration {
  /**
   * How soon to send an incomplete batch after the first metric has been added.
   */
  def batchTimeout: FiniteDuration = 30.seconds

  /**
   * The maximum size of a full batch - once a batch grows to this size it will be sent immediately.
   */
  def sendBatchSize: Int = 100

  /**
   * On failure to communicate with CAVE a batch may be retried, with at most this number of attempts in total.
   * For no retries set this value to 1.
   */
  def maxAttempts: Int = 2

  /**
   * How long to delay before performing a retry on a failed communication with CAVE.
   */
  def retryTimeout: FiniteDuration = 120.seconds

  /**
    * How long to allow the publish call to take.
    */
  def publishTimeout: FiniteDuration = 10.seconds
}
