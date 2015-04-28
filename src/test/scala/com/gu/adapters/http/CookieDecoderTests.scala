package com.gu.adapters.http

import com.gu.core.{Config, User}
import com.gu.identity.cookie.{IdentityCookieDecoder, PreProductionKeys}
import org.scalatest.{FunSuite, Matchers}

import scalaz.\/-

class CookieDecoderTests extends FunSuite with Matchers {

  val decoder = new IdentityCookieDecoder(new PreProductionKeys)

  test("Decode GU_U cookie") {
    val (userId, cookie) = Config.preProdCookie
    val user = CookieDecoder.userFromCookie(decoder, Some(cookie))
    user should be (\/-(User(userId)))
  }

  test("Reject invalid GU_U cookie") {
    val (userId, cookie) = 12356 -> "20394sdkfjs23slkdjfslkjdf234slkdjfsd-23"
    val user = CookieDecoder.userFromCookie(decoder, Some(cookie))
    user.isLeft should be (true)
  }
}