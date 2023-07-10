package logstash

import ch.qos.logback.classic.spi.ILoggingEvent
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.retry.{PredefinedRetryPolicies, RetryPolicy}
import com.amazonaws.services.kinesis.AmazonKinesisAsyncClient
import com.gu.core.pekko.Pekko.system
import com.gu.logback.appender.kinesis.KinesisAppender
import org.apache.pekko.dispatch.MessageDispatcher
import org.apache.pekko.pattern.CircuitBreaker

import java.util.concurrent.ThreadPoolExecutor
import scala.concurrent.Future
import scala.concurrent.duration._

// LogbackOperationsPool must be wired as a singleton
class LogbackOperationsPool() {
  val logbackOperations: MessageDispatcher = system.dispatchers.lookup("pekko.logback-operations")
}

// The KinesisAppender[ILoggingEvent] blocks logging operations on putMessage. This overrides the KinesisAppender api, executing putMessage in an
// independent threadpool
class SafeBlockingKinesisAppender(logbackOperations: LogbackOperationsPool) extends KinesisAppender[ILoggingEvent] {

  private val breaker = new CircuitBreaker(
    system.scheduler,
    maxFailures = 1,
    callTimeout = 1.seconds,
    resetTimeout = 10.seconds
  )(logbackOperations.logbackOperations)

  override protected def createClient(
    credentials: AWSCredentialsProvider,
    configuration: ClientConfiguration, executor: ThreadPoolExecutor): AmazonKinesisAsyncClient = {
    configuration.setMaxErrorRetry(0)
    configuration.setRetryPolicy(
      new RetryPolicy(PredefinedRetryPolicies.NO_RETRY_POLICY.getRetryCondition, PredefinedRetryPolicies.DEFAULT_BACKOFF_STRATEGY, 0, true)
    )
    new AmazonKinesisAsyncClient(credentials, configuration, executor)
  }

  override protected def putMessage(message: String): Unit = {
    breaker.withCircuitBreaker {
      Future {
        super.putMessage(message)
      }(logbackOperations.logbackOperations) // the logbackOperations thread pool is passed explicitly here so blocking on putMessage doesn't affect the logging thread.
    }
    ()
  }
}
