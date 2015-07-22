package com.gu.adapters.notifications

import java.util.concurrent.Executors

import com.amazonaws.ClientConfiguration
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.regions.{ Region, Regions }
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.{ PublishResult, PublishRequest }
import com.gu.adapters.config.Config
import com.gu.adapters.store.AWSCredentials
import com.gu.core.{ Avatar, CreatedAvatar }
import com.typesafe.scalalogging.LazyLogging
import org.json4s.{ Extraction, DefaultFormats }
import org.json4s.native.{ compactJson, renderJValue }

import scala.concurrent.{ Promise, Future }

trait Publisher {
  def publish(arn: String, msg: String, subject: String): Future[String]
}

class SNS(awsRegion: Region, snsTopicArn: String) extends Publisher with LazyLogging {
  val snsClient = new AmazonSNSAsyncClient(AWSCredentials.awsCredentials, new ClientConfiguration(), Executors.newCachedThreadPool())
  snsClient.setRegion(awsRegion)

  def publish(arn: String, msg: String, subject: String): Future[String] = {
    val request = new PublishRequest(snsTopicArn, msg, subject)
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

  def publishAvatar(publisher: Publisher, snsTopicArn: String, eventType: String, avatar: CreatedAvatar): Future[String] = {
    val subject = eventType
    val msg: String = createAvatarMessage(avatar.body)
    publisher.publish(snsTopicArn, msg, subject)
  }
}

