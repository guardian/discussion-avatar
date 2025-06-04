package com.gu.adapters.notifications

import java.util.concurrent.Executors

import com.gu.auth.AWSCredentials
import com.gu.core.models.{Avatar, CreatedAvatar}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.native.{compactJson, renderJValue}
import org.json4s.{DefaultFormats, Extraction}

import scala.concurrent.{Future, Promise}
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.core.client.config.ClientAsyncConfiguration
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.SnsClient
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.Failure

trait Publisher {
  def publish(arn: String, msg: String, subject: String): Future[Unit]
}

case class SnsProperties(awsRegion: Region, snsTopicArn: String)

class SNS(props: SnsProperties) extends Publisher with LazyLogging {
  val snsClient = SnsClient.builder()
    .credentialsProvider(AWSCredentials.awsCredentials)
    .region(props.awsRegion)
    .build()

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  def publish(arn: String, msg: String, subject: String): Future[Unit] = {
    val request = PublishRequest.builder()
      .topicArn(props.snsTopicArn)
      .message(msg)
      .subject(subject)
      .build()

    Future {
      snsClient.publish(request)
      ()
    }.recover {
      case e: Throwable =>
        logger.error(s"Message to $arn has not been sent: $msg", e)
        throw e
    }
  }
}

object Notifications {
  implicit val formats = DefaultFormats

  def createAvatarMessage(avatar: Avatar): String = {
    compactJson(renderJValue(Extraction.decompose(avatar)))
  }

  def publishAvatar(publisher: Publisher, snsTopicArn: String, eventType: String, avatar: CreatedAvatar): Future[Unit] = {
    val subject = eventType
    val msg: String = createAvatarMessage(avatar.body)
    publisher.publish(snsTopicArn, msg, subject)
  }
}

