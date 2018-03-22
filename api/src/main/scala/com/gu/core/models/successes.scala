package com.gu.core.models

sealed trait Success {
  def body: Any
}

sealed trait DeleteResult

case class CreatedAvatar(body: Avatar) extends Success
case class UpdatedAvatar(body: Avatar) extends Success
case class FoundAvatar(body: Avatar) extends Success
case class FoundAvatars(body: List[Avatar], hasMore: Boolean) extends Success
case class UserDeleted(body: User, resources: List[String]) extends Success
case class UserCleaned(body: User, resources: List[String]) extends Success

case class AvatarDeleted(body: Avatar, resources: List[String]) extends DeleteResult
case class AvatarNotDeleted(userId: String) extends DeleteResult {
  def body: String = userId
}
