package com.gu.adapters.notifications

import java.util.concurrent.Executors

import com.amazonaws.ClientConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.{ PublishResult, PublishRequest }
import com.gu.adapters.store.AWSCredentials
import com.gu.adapters.utils.ErrorLogger._
import com.gu.core.{ SNSRequestFailed, Avatar, CreatedAvatar, Config }
import com.typesafe.scalalogging.LazyLogging
import org.json4s.{ Extraction, DefaultFormats }
import org.json4s.native.{ compactJson, renderJValue }

import scala.concurrent.{ Promise, Future }
import scalaz.NonEmptyList

trait Publisher {
  def publish(arn: String, msg: String, subject: String): Future[String]
}

class TestPublisher extends Publisher {
  def publish(arn: String, msg: String, subject: String): Future[String] = {
    Future.successful("123")
  }
}

class SNS extends Publisher with LazyLogging {
  lazy val location = "sns.eu-west-1.amazonaws.com"
  val snsClient = new AmazonSNSAsyncClient(AWSCredentials.awsCredentials, new ClientConfiguration(), Executors.newFixedThreadPool(10))
  snsClient.setEndpoint(location)

  def publish(arn: String, msg: String, subject: String): Future[String] = {
    val request = new PublishRequest(Config.snsTopicArn, msg, subject)
    val p = Promise[String]()

    snsClient.publishAsync(request, new AsyncHandler[PublishRequest, PublishResult]() {
      override def onError(e: Exception) = {
        logger.error(s"message to $arn has not been sent: $msg", e)
        p.failure(e)
      }
      override def onSuccess(request: PublishRequest, result: PublishResult) {
        p.success(result.getMessageId)
      }
    })

    p.future
  }
}

object Notifications {
  implicit val formats = DefaultFormats

  def createAvatarMessage(avatar: Avatar): String = {
    compactJson(renderJValue(Extraction.decompose(avatar)))
  }

  def publishAvatar(publisher: Publisher, eventType: String, avatar: CreatedAvatar): Future[String] = {
    val subject = eventType
    val msg: String = createAvatarMessage(avatar.body)
    publisher.publish(Config.snsTopicArn, msg, subject)
  }
}

