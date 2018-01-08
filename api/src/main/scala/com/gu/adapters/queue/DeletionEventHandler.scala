package com.gu.adapters.queue

import akka.stream.alpakka.sqs.{Ack, MessageAction, RequeueWithDelay}
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.adapters.store.AWSCredentials
import com.gu.core.akka.Akka
import com.gu.core.akka.Akka._
import com.gu.core.models.{Error, InvalidUserId, User}
import com.gu.core.store.AvatarStore
import com.typesafe.scalalogging.LazyLogging
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.{NonEmptyList, \/}
import scala.language.postfixOps

case class DeletionMessage(userId: String)

case class DeletionEvent(Message: String) {
  import DeletionEvent._
  def userId: Option[String] = {
    for {
      messageJson <- parseOpt(Message)
      deletionMessage <- messageJson.extractOpt[DeletionMessage]
    } yield deletionMessage.userId
  }
}

object DeletionEvent {
  implicit val jsonFormats: Formats = DefaultFormats

  def deletionEvent(event: String): Option[String] = {
    for {
      deletionEventJson <- parseOpt(event)
      deletionEvent <- deletionEventJson.extractOpt[DeletionEvent]
      userId <- deletionEvent.userId
    } yield userId
  }
}

case class DeletionEventProps(queueUrl: String, region: String)

class DeletionEventHandler(props: DeletionEventProps, avatarStore: AvatarStore) extends LazyLogging {
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

  def deleteUser(m: Message): Future[MessageAction] = Akka.executeBlocking {
    val event = DeletionEvent.deletionEvent(m.getBody)
    val eventUserId = event.toRight(InvalidUserId("Unable to get userId from sqs message", NonEmptyList(m.getBody)))

    val result: \/[Error, Ack] = for {
      userId <- \/.fromEither(eventUserId)
      user <- User.userFromId(userId)
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
