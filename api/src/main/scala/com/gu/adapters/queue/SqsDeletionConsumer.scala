package com.gu.adapters.queue

import java.net.URI

import akka.stream.alpakka.sqs.MessageAction
import akka.stream.alpakka.sqs.MessageAction.{Delete, Ignore}
import akka.stream.alpakka.sqs.scaladsl.{SqsAckSink, SqsSource}
import com.github.matsluni.akkahttpspi.AkkaHttpClient
import com.gu.auth.AWSCredentials
import com.gu.core.akka.Akka
import com.gu.core.akka.Akka._
import com.gu.core.models.{DeletionEvent, Error, InvalidUserId, User}
import com.gu.core.store.AvatarStore
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scalaz.{NonEmptyList, \/}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

import scala.language.postfixOps

case class SqsDeletionConsumerProps(queueUrl: String, region: String)

class SqsDeletionConsumer(props: SqsDeletionConsumerProps, avatarStore: AvatarStore) extends LazyLogging {
  private lazy val client = SqsAsyncClient
    .builder()
    .credentialsProvider(AWSCredentials.awsCredentialsV2)
    .region(Region.of(props.region))
    .httpClient(AkkaHttpClient.builder().withActorSystem(system).build())
    .endpointOverride(new URI(props.queueUrl))
    .build()

  def listen(): Future[Unit] = {
    SqsSource(props.queueUrl)(client)
      .mapAsync(1)(m => SqsDeletionConsumer.deleteUser(m, avatarStore))
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

    val eventUserId = DeletionEvent.userId(m.body()).toRight(InvalidUserId("Unable to get userId from sqs message", NonEmptyList(m.body)))

    val result: \/[Error, MessageAction] = for {
      userId <- \/.fromEither(eventUserId)
      user <- User.userFromId(userId)
      deleted <- avatarStore.deleteAll(user)
    } yield {
      logger.info(s"Successfully deleted $deleted")
      Delete(m)
    }
    result.leftMap { e =>
      logger.error(s"Failed to process delete event $m", e)
      // If the avatar can't be deleted, don’t change that message, and let it reappear in the queue after the visibility timeout
      Ignore(m)
    }.merge
  }
}
