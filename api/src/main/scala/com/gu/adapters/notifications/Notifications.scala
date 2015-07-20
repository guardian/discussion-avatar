package com.gu.adapters.notifications

import java.util.concurrent.Future

import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.{ PublishResult, PublishRequest }
import com.gu.adapters.utils.ErrorLogger._
import com.gu.core.{ SNSRequestFailed, Avatar, CreatedAvatar, Config }
import org.json4s.{ Extraction, DefaultFormats }
import org.json4s.native.{ compactJson, renderJValue }

import scalaz.NonEmptyList

object Notifications {
  implicit val formats = DefaultFormats


  def createAvatarMessage(avatar: Avatar): String = {
    compactJson(renderJValue(Extraction.decompose(avatar)))
  }

  def avatarPublisher(snsClient: AmazonSNSAsyncClient, eventType: String, avatar: CreatedAvatar): Future[PublishResult] = {
    val subject = eventType
    val msg: String = createAvatarMessage(avatar.body)

    val request = new PublishRequest(Config.snsTopicArn, msg, subject)
    snsClient.publishAsync(request, new AsyncHandler[PublishRequest, PublishResult]() {

      override def onError(e: Exception) = {
        val exception = NonEmptyList(e.toString)
        val error = SNSRequestFailed(s"message to ${Config.snsTopicArn} has not been sent: $msg", exception)
        logError(s"message to ${Config.snsTopicArn} has not been sent: $msg", error)

      }
      override def onSuccess(request: PublishRequest, result: PublishResult) {}
    })
  }
}

