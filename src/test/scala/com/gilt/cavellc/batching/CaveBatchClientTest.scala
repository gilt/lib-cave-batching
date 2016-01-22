package com.gilt.cavellc.batching

import java.util.concurrent.atomic.AtomicInteger

import com.gilt.cavellc.models.Metric
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

class CaveBatchClientTest extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private def newFixture(f: => Future[Unit] = Future.successful(Unit)) = new {
    val callCounter = new AtomicInteger()
    val metrics = new ListBuffer[Metric]

    def call(m: Seq[Metric]): Future[Unit] = {
      callCounter.incrementAndGet()
      metrics ++= m
      f
    }

    val batchConfig = mock[CaveBatchConfiguration]
    val client = new CaveBatchClient(batchConfig, call)
  }

  private def doConfig(batchConfig: CaveBatchConfiguration,
                       sendTimeout: FiniteDuration = 10.milliseconds,
                       publishTimeout: FiniteDuration = 10.milliseconds,
                       batchSize: Int = 10,
                       maxAttempts: Int = 2,
                       retryTimeout: FiniteDuration = 20.milliseconds) = {
    doReturn(publishTimeout).when(batchConfig).publishTimeout
    doReturn(sendTimeout).when(batchConfig).sendTimeout
    doReturn(batchSize).when(batchConfig).sendBatchSize
    doReturn(retryTimeout).when(batchConfig).retryTimeout
    doReturn(maxAttempts).when(batchConfig).maxAttempts
  }

  "CaveBatchClient" should "send no batches when no metrics are passed in" in {
    val fixture = newFixture()

    doConfig(fixture.batchConfig, sendTimeout = 1.millisecond)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    fixture.callCounter.get shouldBe 0
  }

  it should "send only a single batch when a single metric is passed in" in {
    val fixture = newFixture()

    doConfig(fixture.batchConfig, sendTimeout = 1.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    fixture.client.createMetric(metric1)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    fixture.callCounter.get shouldBe 1
    fixture.metrics(0) shouldBe metric1
  }

  it should "send a batch once a batch is full" in {
    // easily guarantee concurrent updates
    var afterNanos = Long.MaxValue

    def timedFuture: Future[Unit] = {
      afterNanos = System.nanoTime
      Future.successful(Unit)
    }

   val fixture = newFixture(timedFuture)

    doConfig(fixture.batchConfig, sendTimeout = 1.second, batchSize = 3)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    val metric2 = Metric("met2", Some(Map("a" -> "b")), 182673L, 123.4D)
    val metric3 = Metric("met3", Some(Map("a1" -> "b1")), 182673L, 123.4D)

    val beforeNanos = System.nanoTime

    fixture.client.createMetric(metric1)
    fixture.client.createMetric(metric2)
    fixture.client.createMetric(metric3)

    fixture.callCounter.get shouldBe 1
    fixture.metrics(0) shouldBe metric1
    fixture.metrics(1) shouldBe metric2
    fixture.metrics(2) shouldBe metric3

    (afterNanos - beforeNanos) shouldBe < (fixture.batchConfig.sendTimeout.toNanos)
  }

  it should "continue to send batches after the first one so long as metrics feed through" in {
    val fixture = newFixture()

    doConfig(fixture.batchConfig, sendTimeout = 10.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    val metric2 = Metric("met2", None, 182673L, 123.4D)
    val metric3 = Metric("met3", Some(Map("a" -> "b")), 182673L, 123.4D)

    fixture.client.createMetric(metric1)
    Thread.sleep(100)
    fixture.client.createMetric(metric2)
    Thread.sleep(100)
    fixture.client.createMetric(metric3)
    Thread.sleep(100)

    fixture.callCounter.get shouldBe 3
    fixture.metrics(0) shouldBe metric1
    fixture.metrics(1) shouldBe metric2
    fixture.metrics(2) shouldBe metric3
  }

  it should "retry sending a batch while there remote call fails" in {
    val fixture = newFixture(Future.failed(new Exception("simulated comms failure")))

    doConfig(fixture.batchConfig, sendTimeout = 1.millisecond, maxAttempts = 5, retryTimeout = 5.milliseconds)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    fixture.client.createMetric(metric1)

    Thread.sleep(500) // Sleep for much longer than the send timeout...

    fixture.callCounter.get shouldBe 5
    fixture.metrics(0) shouldBe metric1
    fixture.metrics(1) shouldBe metric1
    fixture.metrics(2) shouldBe metric1
    fixture.metrics(3) shouldBe metric1
    fixture.metrics(4) shouldBe metric1
  }

  it should "retry sending a batch while there remote call is slow" in {
    val fixture = newFixture(Promise().future)

    doConfig(fixture.batchConfig, sendTimeout = 1.millisecond, maxAttempts = 5, retryTimeout = 5.milliseconds)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    fixture.client.createMetric(metric1)

    Thread.sleep(500) // Sleep for much longer than the send timeout...

    fixture.callCounter.get shouldBe 5
    fixture.metrics(0) shouldBe metric1
    fixture.metrics(1) shouldBe metric1
    fixture.metrics(2) shouldBe metric1
    fixture.metrics(3) shouldBe metric1
    fixture.metrics(4) shouldBe metric1
  }
}
