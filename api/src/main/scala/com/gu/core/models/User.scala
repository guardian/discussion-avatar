package com.gu.core.models

import com.gu.core.utils.ErrorHandling._
import com.gu.core.models.Errors._

import scalaz.{NonEmptyList, \/}

case class User(id: String)

object User {
  def userFromId(userId: String): Error \/ User = {
    attempt(User(userId))
      .leftMap(_ => invalidUserId(NonEmptyList("Expected userId, found: " + userId)))
  }
}
