package com.gu.core.models

sealed trait Success {
  def body: Any
}

case class CreatedAvatar(body: Avatar) extends Success
case class UpdatedAvatar(body: Avatar) extends Success
case class FoundAvatar(body: Avatar) extends Success
case class FoundAvatars(body: List[Avatar], hasMore: Boolean) extends Success
case class UserDeleted(body: User, resources: List[String]) extends Success