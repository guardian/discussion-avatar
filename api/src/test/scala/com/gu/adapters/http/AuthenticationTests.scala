package com.gu.adapters.http

import com.gu.core.models.User
import org.scalatest.{ FunSuite, Matchers }

import scalaz.\/-

class AuthenticationTests extends FunSuite with Matchers {

  val decoder = StubGuUDecoder

  test("Decode GU_U cookie from Authorization header") {
    val authHeader = "Bearer cookie=" + TestCookie.fakeGuu
    val user = CookieDecoder.userFromHeader(decoder, Some(authHeader))
    user should be(\/-(User(TestCookie.userId)))
  }

  test("Decode SC_GU_U cookie from cookie") {
    val user = CookieDecoder.userFromCookie(decoder, Some(TestCookie.fakeScguu))
    user should be(\/-(User(TestCookie.userId)))
  }

  test("Reject invalid cookie") {
    val authHeader = "Bearer cookie=" + "20394sdkfjs23slkdjfslkjdf234slkdjfsd-23"
    val user = CookieDecoder.userFromHeader(decoder, Some(authHeader))
    user.isLeft should be(true)
  }
}