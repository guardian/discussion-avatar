package com.gu.adapters.notifications

import java.util.concurrent.Executors

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.amazonaws.services.sns.model.PublishRequest
import com.gu.adapters.store.AWSCredentials
import com.gu.core.{ Avatar, CreatedAvatar, Config }
import org.json4s.{ Extraction, DefaultFormats }
import org.json4s.native.{ compactJson, renderJValue }

object Notifications {
  implicit val formats = DefaultFormats

  lazy val location = "sns.eu-west-1.amazonaws.com"
  val snsClient = new AmazonSNSAsyncClient(AWSCredentials.awsCredentials, new ClientConfiguration(), Executors.newCachedThreadPool())
  snsClient.setEndpoint(location)

  def createAvatarMessage(avatar: Avatar): String = {
    println(compactJson(renderJValue(Extraction.decompose(avatar))))
    compactJson(renderJValue(Extraction.decompose(avatar)))
  }

  def avatarPublisher(eventType: String, avatar: CreatedAvatar) = {
    new AvatarPublisher(eventType, avatar, snsClient)
  }

  case class avatarMessage(avatarId: String)

  class AvatarPublisher(eventType: String, avatar: CreatedAvatar, snsClient: AmazonSNSAsyncClient) {
    val subject = eventType
    val msg: String = createAvatarMessage(avatar.body)
    try {
      val request = new PublishRequest(Config.snsTopicArn, msg, subject)
      snsClient.publishAsync(request)
    } catch {
      case e: Exception => {
        e.printStackTrace()
      }
    }
  }

}

