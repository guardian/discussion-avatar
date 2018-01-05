package com.gu.adapters.queue

import akka.stream.alpakka.sqs.{Ack, MessageAction, RequeueWithDelay}
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.adapters.store.AWSCredentials
import com.gu.core.akka.Akka
import com.gu.core.akka.Akka._
import com.gu.core.models.Errors.invalidUserId
import com.gu.core.models.{Error, User}
import com.gu.core.store.AvatarStore
import com.gu.core.utils.ErrorHandling.attempt
import com.typesafe.scalalogging.LazyLogging
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.{NonEmptyList, \/}
import scala.language.postfixOps

case class DeletionEvent(userId: String)

case class DeletionEventProps(queueUrl: String, region: String)

class DeletionEventHandler(props: DeletionEventProps, avatarStore: AvatarStore) extends LazyLogging {
  implicit val jsonFormats: Formats = DefaultFormats

  private val client: AmazonSQSAsync = AmazonSQSAsyncClientBuilder
    .standard()
    .withCredentials(AWSCredentials.awsCredentials)
    .withEndpointConfiguration(new EndpointConfiguration(props.queueUrl, props.region))
    .build()

  def sqsQueue(): Future[Unit] = {
    SqsSource(props.queueUrl)(client)
      .mapAsync(1)(m => deleteUser(m).map((m, _)))
      .runWith(SqsAckSink(props.queueUrl)(client))
      .failed
      .flatMap { e =>
        logger.error("Sqs queue error, restarting consumer in 5 seconds", e)
        akka.pattern.after(5 seconds, Akka.scheduler)(sqsQueue())
      }
  }

  private def userFromId(userId: String): Error \/ User = {
    attempt(User(userId))
      .leftMap(_ => invalidUserId(NonEmptyList("Expected userId, found: " + userId)))
  }

  private def deleteUser(m: Message): Future[MessageAction] = Akka.executeBlocking {
    val event = parse(m.getBody).extract[DeletionEvent]
    val result: \/[Error, Ack] = for {
      user <- userFromId(event.userId)
      deleted <- avatarStore.deleteAll(user, isDryRun = false)
    } yield {
      logger.info(s"Successfully deleted $deleted")
      Ack()
    }
    result.leftMap { e =>
      logger.error(s"Failed to process delete event $m", e)
      RequeueWithDelay(10)
    }.merge
  }

}
