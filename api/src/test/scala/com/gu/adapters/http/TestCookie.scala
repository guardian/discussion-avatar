package com.gu.adapters.http

import com.gu.identity.cookie.GuUDecoder
import com.gu.identity.model.User

object TestCookie {
  val userId = 654321
  val fakeScguu = "valid-sc-gu-u"
  val testSecureCookie = userId -> fakeScguu
}

object StubGuUDecoder extends GuUDecoder(null) {
  import TestCookie._

  override def getUserDataForScGuU(cookieValue: String): Option[User] = {
    if (cookieValue contains fakeScguu) {
      val user = User().copy(primaryEmailAddress = "user@test.com", id = userId.toString)
      Some(user)
    } else
      None
  }
}