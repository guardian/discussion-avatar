package com.gu.adapters.http

import com.gu.adapters.utils.ErrorHandling.attempt
import com.gu.core.Errors._
import com.gu.core.{ Config, Error, User }
import com.gu.identity.cookie.IdentityCookieDecoder

import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object TokenAuth {

  val apiKeys = Config.apiKeys

  def isValidKey(authHeader: Option[String]): Error \/ String = {

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

  def userFromHeader(decoder: IdentityCookieDecoder, authHeader: Option[String]): Error \/ User = {
    val guu = authHeader.map(_.stripPrefix("Bearer cookie="))

    val user = for {
      cook <- guu.toRightDisjunction("No GU_U cookie in request")
      user <- attempt(decoder.getUserDataForGuU(cook)).toOption.flatten.map(_.user)
        .toRightDisjunction("Unable to extract user data from Authorization header")
    } yield User(user.id.toInt)

    user.leftMap(error => userAuthorizationFailed(NonEmptyList(error)))
  }
}
