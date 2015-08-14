package com.gu.adapters.http

import com.gu.core.models.User
import com.gu.identity.cookie.{ IdentityCookieDecoder, PreProductionKeys }
import org.scalatest.{ FunSuite, Matchers }
import com.gu.adapters.http.Cookies.preProdCookie

import scalaz.\/-

class AuthenticationTests extends FunSuite with Matchers {

  val decoder = new IdentityCookieDecoder(new PreProductionKeys)

  test("Decode GU_U cookie from Authorization header") {
    val (userId, cookie) = preProdCookie
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