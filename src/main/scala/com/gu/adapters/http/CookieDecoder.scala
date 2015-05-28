package com.gu.adapters.http

import com.gu.adapters.utils.Attempt.attempt
import com.gu.core.Errors._
import com.gu.core.{ Error, User }
import com.gu.identity.cookie.IdentityCookieDecoder

import scalaz.std.option.optionSyntax._
import scalaz.{ NonEmptyList, \/ }

object CookieDecoder {

  def userFromHeader(decoder: IdentityCookieDecoder, authHeader: Option[String]): Error \/ User = {
    val guu = authHeader.map(_.stripPrefix("Bearer "))

    val user = for {
      cook <- guu.toRightDisjunction("No GU_U cookie in request")
      user <- attempt(decoder.getUserDataForGuU(cook)).toOption.flatten.map(_.user)
        .toRightDisjunction("Unable to extract user data from Authorization header")
    } yield User(user.id.toInt)

    user.leftMap(error => userAuthorizationFailed(NonEmptyList(error)))
  }
}
