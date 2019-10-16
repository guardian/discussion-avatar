package com.gu.adapters.http

import com.gu.core.models.User
import org.scalatest.{ FunSuite, Matchers }

import scalaz.\/-

class AuthenticationTests extends FunSuite with Matchers {

  val decoder = StubGuUDecoder

  test("Decode cookie") {
    val user = AuthenticationService.userFromCookie(decoder, Some(TestCookie.fakeScguu))
    user should be(\/-(User(TestCookie.userId)))
  }

  test("Reject invalid cookie") {
    val authHeader = "Bearer cookie=" + "20394sdkfjs23slkdjfslkjdf234slkdjfsd-23"
    val user = AuthenticationService.userFromCookie(decoder, Some("bad-cookie-value"))
    user.isLeft should be(true)
  }
}