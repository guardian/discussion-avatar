package com.gu.adapters.http

import com.gu.core.models.Errors._
import com.gu.core.models.{ Error, User }
import com.gu.core.utils.ErrorHandling.attempt
import com.gu.identity.cookie.GuUDecoder

import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object TokenAuth {

  def isValidKey(authHeader: Option[String], apiKeys: List[String]): Error \/ String = {

    val tokenHeader = "Bearer token="
    val token = authHeader.withFilter(_.startsWith(tokenHeader)).map(_.stripPrefix(tokenHeader))

    val tokenOrError = token match {
      case Some(valid) if apiKeys.contains(valid) => valid.right
      case Some(invalid) => "Invalid access token provided".left
      case None => "No access token in request".left
    }
    tokenOrError.leftMap(error => tokenAuthorizationFailed(NonEmptyList(error)))
  }
}

object CookieDecoder {

  def userFromHeaderOrCookie(decoder: GuUDecoder, authHeader: Option[String], cookie: Option[String]): Error \/ User = {
    userFromHeader(decoder, authHeader) orElse userFromCookie(decoder, cookie)
  }

  def userFromHeader(decoder: GuUDecoder, authHeader: Option[String]): Error \/ User = {
    val guu = authHeader.map(_.stripPrefix("Bearer cookie="))

    // TODO remove support for GU_U once confident SC_GU_U works and clients have switched over
    val guuUser = for {
      cookie <- guu.toRightDisjunction("No GU_U cookie in request")
      user <- attempt(decoder.getUserDataForGuU(cookie)).toOption.flatten.map(_.user)
        .toRightDisjunction("Unable to extract user data from Authorization header")
    } yield User(user.id.toInt)

    guuUser.leftMap(error => userAuthorizationFailed(NonEmptyList(error)))
  }

  def userFromCookie(decoder: GuUDecoder, cookie: Option[String]): Error \/ User = {
    val authedUser = for {
      c <- cookie.toRightDisjunction("No secure cookie in request")
      user <- readCookie(decoder, c)
    } yield user

    authedUser.leftMap(error => userAuthorizationFailed(NonEmptyList(error)))
  }

  private[this] def readCookie(decoder: GuUDecoder, cookie: String): String \/ User = {
    for {
      user <- attempt(decoder.getUserDataForScGuU(cookie))
        .toOption.flatten.toRightDisjunction("Unable to extract user data from Authorization header")
    } yield User(user.getId.toInt)
  }
}
