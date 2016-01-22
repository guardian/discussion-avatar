package com.gu.adapters.http

import com.gu.identity.cookie.{ GuUCookieData, GuUDecoder }
import com.gu.identity.model.User

object TestCookie {
  val userId = 654321
  val fakeGuu = "valid-gu-u"
  val fakeScguu = "valid-sc-gu-u"
  val testCookie = userId -> fakeGuu
  val testSecureCookie = userId -> fakeScguu
}

object StubGuUDecoder extends GuUDecoder(null) {
  import TestCookie._

  override def getUserDataForGuU(cookieValue: String): Option[GuUCookieData] = {
    if (cookieValue contains fakeGuu) {
      val user = User().copy(primaryEmailAddress = "user@test.com", id = userId.toString)
      Some(GuUCookieData(user, 0, None))
    } else
      None
  }

  override def getUserDataForScGuU(cookieValue: String): Option[User] = {
    if (cookieValue contains fakeScguu) {
      val user = User().copy(primaryEmailAddress = "user@test.com", id = userId.toString)
      Some(user)
    } else
      None
  }
}