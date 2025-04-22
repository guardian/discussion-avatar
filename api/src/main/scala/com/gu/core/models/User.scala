package com.gu.core.models

import com.gu.core.utils.ErrorHandling._
import com.gu.core.models.Errors._

case class User(id: String)

object User {
  def userFromId(userId: String): Either[Error, User] = {
    attempt(User(userId))
      .toEither
      .left.map(_ => invalidUserId(List("Expected userId, found: " + userId)))
  }
}
