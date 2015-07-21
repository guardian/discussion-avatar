package com.gu.adapters.http

import com.gu.adapters.config.Config
import com.gu.core.User
import com.gu.identity.cookie.{ IdentityCookieDecoder, PreProductionKeys }
import org.scalatest.{ FunSuite, Matchers }

import scalaz.\/-

class AuthenticationTests extends FunSuite with Matchers {

  val decoder = new IdentityCookieDecoder(new PreProductionKeys)

  test("Decode GU_U cookie from Authorization header") {
    val (userId, cookie) = Config.preProdCookie
    val authHeader = "Bearer cookie=" + cookie
    val user = CookieDecoder.userFromHeader(decoder, Some(authHeader))
    user should be(\/-(User(userId)))
  }

  test("Reject invalid GU_U cookie") {
    val (userId, cookie) = 12356 -> "20394sdkfjs23slkdjfslkjdf234slkdjfsd-23"
    val authHeader = "Bearer " + cookie
    val user = CookieDecoder.userFromHeader(decoder, Some(authHeader))
    user.isLeft should be(true)
  }
}