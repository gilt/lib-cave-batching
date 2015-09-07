package client

import com.gilt.cavellc.Metrics
import com.gilt.cavellc.models.Metric
import org.mockito.Matchers.{any, anyString, eq => mockEq}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class CaveBatchClientTest extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  private var metricsClient: Metrics = _
  private var batchConfig: CaveBatchConfiguration = _
  private var client: CaveBatchClient = _

  override def beforeEach() = {
    metricsClient = mock[Metrics]
    batchConfig = mock[CaveBatchConfiguration]
    client = new CaveBatchClient(batchConfig, metricsClient)
  }

  override def afterEach() = {
    client.shutdown()
  }

  private def neverFuture[A]: Future[A] = Promise[A]().future

  private def doConfig(orgName: String = "orgname",
                       teamName: String = "teamname",
                       sendTimeout: FiniteDuration = 10.milliseconds,
                       batchSize: Int = 10,
                       maxAttempts: Int = 2,
                       retryTimeout: FiniteDuration = 20.milliseconds) = {
    doReturn(orgName).when(batchConfig).organisationName
    doReturn(teamName).when(batchConfig).teamName
    doReturn(sendTimeout).when(batchConfig).sendTimeout
    doReturn(batchSize).when(batchConfig).sendBatchSize
    doReturn(retryTimeout).when(batchConfig).retryTimeout
    doReturn(maxAttempts).when(batchConfig).maxAttempts
  }

  "CaveBatchClient" should "send no batches when no metrics are passed in" in {
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    verify(metricsClient, never).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    verify(metricsClient, never).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])
  }

  it should "send only a single batch when a single organisation metric is passed in" in {
    doReturn(neverFuture[Try[Boolean]]).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    client.createOrganisationMetric(metric1)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    verify(metricsClient, times(1)).postOrganizationsByOrganization(mockEq("orgname"), mockEq(List(metric1)))(any[ExecutionContext])
    verify(metricsClient, never).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])
  }

  it should "send only a single batch when a single team metric is passed in" in {
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(neverFuture[Try[Boolean]]).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    client.createTeamMetric(metric1)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    verify(metricsClient, never).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric1)))(any[ExecutionContext])
  }

  it should "send both an organisation batch and a team batch when both types of metrics are submitted" in {
    doReturn(neverFuture[Try[Boolean]]).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(neverFuture[Try[Boolean]]).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    val metric2 = Metric("met2", Some(Map("a" -> "b")), 1282673L, 123.45D)
    client.createTeamMetric(metric1)
    client.createOrganisationMetric(metric2)

    Thread.sleep(100) // Sleep for much longer than the send timeout...

    verify(metricsClient, times(1)).postOrganizationsByOrganization(mockEq("orgname"), mockEq(List(metric2)))(any[ExecutionContext])
    verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric1)))(any[ExecutionContext])
  }

  it should "send a batch once a batch is full" in {
    // easily guarantee concurrent updates
    var afterNanos = Long.MaxValue

    doAnswer(new Answer[Future[Try[Boolean]]] {
      override def answer(invocation: InvocationOnMock) = {
        afterNanos = System.nanoTime
        neverFuture[Try[Boolean]]
      }
    }).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.second, batchSize = 3)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    val metric2 = Metric("met2", Some(Map("a" -> "b")), 182673L, 123.4D)
    val metric3 = Metric("met3", Some(Map("a1" -> "b1")), 182673L, 123.4D)

    val beforeNanos = System.nanoTime

    client.createTeamMetric(metric1)
    client.createTeamMetric(metric2)
    client.createTeamMetric(metric3)

    verify(metricsClient, never).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric1, metric2, metric3)))(any[ExecutionContext])

    (afterNanos - beforeNanos) < batchConfig.sendTimeout.toNanos shouldEqual true
  }

  it should "continue to send batches after the first one so long as metrics feed through" in {
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(neverFuture[Try[Boolean]]).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 10.millisecond)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    val metric2 = Metric("met2", None, 182673L, 123.4D)
    val metric3 = Metric("met3", Some(Map("a" -> "b")), 182673L, 123.4D)

    client.createTeamMetric(metric1)
    Thread.sleep(100)
    client.createTeamMetric(metric2)
    Thread.sleep(100)
    client.createTeamMetric(metric3)
    Thread.sleep(100)

    val ino = Mockito.inOrder(metricsClient)
    ino.verify(metricsClient, never).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    ino.verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric1)))(any[ExecutionContext])
    ino.verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric2)))(any[ExecutionContext])
    ino.verify(metricsClient, times(1)).postOrganizationsAndTeamsByOrganizationAndTeam(mockEq("orgname"), mockEq("teamname"), mockEq(List(metric3)))(any[ExecutionContext])
  }

  it should "retry sending a batch while there is communication problems" in {
    doReturn(Future.failed(new Exception("simulated comms failure"))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond, maxAttempts = 5, retryTimeout = 5.milliseconds)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    client.createOrganisationMetric(metric1)

    Thread.sleep(500) // Sleep for much longer than the send timeout...

    verify(metricsClient, times(batchConfig.maxAttempts)).postOrganizationsByOrganization(mockEq("orgname"), mockEq(List(metric1)))(any[ExecutionContext])
    verify(metricsClient, never).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])
  }

  it should "not retry sending a batch for a CAVE rejection" in {
    doReturn(Future.successful(Success(false))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond, maxAttempts = 5, retryTimeout = 5.milliseconds)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    client.createOrganisationMetric(metric1)

    Thread.sleep(500) // Sleep for much longer than the send timeout...

    verify(metricsClient, times(1)).postOrganizationsByOrganization(mockEq("orgname"), mockEq(List(metric1)))(any[ExecutionContext])
    verify(metricsClient, never).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])
  }

  it should "not retry sending a batch for an unknown CAVE rejection" in {
    doReturn(Future.successful(Failure(new Exception("testing unknown failure")))).when(metricsClient).postOrganizationsByOrganization(anyString, any[List[Metric]])(any[ExecutionContext])
    doReturn(Future.failed(new Exception("unexpected call"))).when(metricsClient).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])

    doConfig(sendTimeout = 1.millisecond, maxAttempts = 5, retryTimeout = 5.milliseconds)

    val metric1 = Metric("met1", None, 182673L, 123.4D)
    client.createOrganisationMetric(metric1)

    Thread.sleep(500) // Sleep for much longer than the send timeout...

    verify(metricsClient, times(1)).postOrganizationsByOrganization(mockEq("orgname"), mockEq(List(metric1)))(any[ExecutionContext])
    verify(metricsClient, never).postOrganizationsAndTeamsByOrganizationAndTeam(anyString, anyString, any[List[Metric]])(any[ExecutionContext])
  }
}
