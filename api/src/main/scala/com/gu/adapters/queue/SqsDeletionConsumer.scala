package com.gu.adapters.queue

import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.MessageAction.{Delete, Ignore}
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.{AmazonSQSAsync, AmazonSQSAsyncClientBuilder}
import com.gu.auth.AWSCredentials
import com.gu.core.akka.Akka
import com.gu.core.akka.Akka._
import com.gu.core.models.{DeletionEvent, Error, InvalidUserId, User}
import com.gu.core.store.AvatarStore
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.{NonEmptyList, \/}
import scala.language.postfixOps

case class SqsDeletionConsumerProps(queueUrl: String, region: String)

class SqsDeletionConsumer(props: SqsDeletionConsumerProps, avatarStore: AvatarStore) extends LazyLogging {
  private lazy val client: AmazonSQSAsync = AmazonSQSAsyncClientBuilder
    .standard()
    .withCredentials(AWSCredentials.awsCredentials)
    .withEndpointConfiguration(new EndpointConfiguration(props.queueUrl, props.region))
    .build()

  def listen(): Future[Unit] = {
    SqsSource(props.queueUrl)(client)
      .mapAsync(1)(m => SqsDeletionConsumer.deleteUser(m, avatarStore).map((m, _)))
      .runWith(SqsAckSink(props.queueUrl)(client))
      .failed
      .flatMap { e =>
        logger.error("Sqs queue error, restarting consumer in 5 seconds", e)
        akka.pattern.after(5 seconds, Akka.scheduler)(listen())
      }
  }
}

object SqsDeletionConsumer extends LazyLogging {
  def deleteUser(m: Message, avatarStore: AvatarStore): Future[MessageAction] = Akka.executeBlocking {
    val eventUserId = DeletionEvent.userId(m.getBody).toRight(InvalidUserId("Unable to get userId from sqs message", NonEmptyList(m.getBody)))

    val result: \/[Error, MessageAction] = for {
      userId <- \/.fromEither(eventUserId)
      user <- User.userFromId(userId)
      deleted <- avatarStore.deleteAll(user)
    } yield {
      logger.info(s"Successfully deleted $deleted")
      Delete
    }
    result.leftMap { e =>
      logger.error(s"Failed to process delete event $m", e)
      // If the avatar can't be deleted, don’t change that message, and let it reappear in the queue after the visibility timeout
      Ignore
    }.merge
  }
}
