package com.gu.adapters.http

import com.gu.core.Errors._
import com.gu.core.{Error, User}
import com.gu.identity.cookie.IdentityCookieDecoder

import scala.util.Try
import scalaz.std.option.optionSyntax._
import scalaz.{NonEmptyList, \/}

object CookieDecoder {

  def userFromCookie(decoder: IdentityCookieDecoder, cookie: Option[String]): Error \/ User = {
    val user = for {
      cook <- cookie \/> "No GU_U cookie in request"
      user <- Try(decoder.getUserDataForGuU(cook)).toOption.flatten.map(_.user) \/> "Unable to extract user data from cookie"
    } yield User(user.id.toInt)

    user.leftMap(error => unableToReadUserCookie(NonEmptyList(error)))
  }
}
