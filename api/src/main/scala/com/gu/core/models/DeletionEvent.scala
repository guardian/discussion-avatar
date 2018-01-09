package com.gu.core.models

import org.json4s.{DefaultFormats, Formats}
import org.json4s.jackson.JsonMethods.parseOpt

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

  def userId(event: String): Option[String] = {
    for {
      deletionEventJson <- parseOpt(event)
      deletionEvent <- deletionEventJson.extractOpt[DeletionEvent]
      userId <- deletionEvent.userId
    } yield userId
  }
}