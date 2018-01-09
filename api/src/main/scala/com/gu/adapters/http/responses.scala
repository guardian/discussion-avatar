package com.gu.adapters.http

import com.gu.adapters.http.Links._
import com.gu.core.models.{Avatar, UserDeleted}

import scalaz.NonEmptyList

case class Link(rel: String, href: String)

sealed trait Argo
case class Message(uri: Option[String], data: Map[String, String], links: List[Link]) extends Argo
case class ErrorResponse(uri: Option[String], message: String, errors: List[String]) extends Argo
case class CreatedAvatarResponse(uri: Option[String], data: Avatar) extends Argo
case class AvatarResponse(uri: Option[String], data: Avatar, links: List[Link]) extends Argo
case class AvatarsResponse(uri: Option[String], data: List[AvatarResponse], links: List[Link]) extends Argo
case class DeletedUserResponse(uri: Option[String], data: UserDeleted, links: List[Link]) extends Argo

object Message {
  def apply(msg: Map[String, String]): Message = Message(None, msg, Nil)
}

object AvatarResponse {
  def apply(avatar: Avatar, req: Req): AvatarResponse = {
    AvatarResponse(
      Some(s"${req.base}/avatars/${avatar.id}"),
      avatar,
      Nil
    )
  }
}

object AvatarsResponse {
  def apply(avatars: List[Avatar], req: Req, hasMore: Boolean, pageSize: Int): AvatarsResponse = {
    val ls = links(avatars, req, hasMore, pageSize)
    val data = avatars.map(a => AvatarResponse(a, req))
    AvatarsResponse(Some(req.base + req.path), data, ls)
  }
}

object ErrorResponse {
  def apply(msg: String): ErrorResponse = {
    ErrorResponse(None, msg, Nil)
  }

  def apply(msg: String, errors: NonEmptyList[String]): ErrorResponse = {
    ErrorResponse(None, msg, errors.list)
  }
}