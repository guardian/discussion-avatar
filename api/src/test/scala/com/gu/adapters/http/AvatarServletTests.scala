package com.gu.adapters.http

import java.io.File

import com.gu.adapters.config.Config
import com.gu.adapters.notifications.TestPublisher
import com.gu.adapters.store.{ TestFileStore, TestKVStore }
import com.gu.core.models.{ Approved, Inactive, Pending, Rejected }
import com.gu.core.store.AvatarStore
import com.gu.utils.TestHelpers
import com.gu.adapters.http.TestCookie.testCookie

class AvatarServletTests extends TestHelpers {

  val config = Config()
  val avatarServletProps = config.avatarServletProperties.copy(cookieDecoder = StubGuUDecoder)
  val storeProps = config.storeProperties
  implicit val swagger = new AvatarSwagger

  // NOTE: This won't compile if you don't explicitly specify the type due to compiler bug.
  // See https://issues.scala-lang.org/browse/SI-5091
  val apiUrl: String = avatarServletProps.apiUrl
  val apiKey = avatarServletProps.apiKeys.head

  addServlet(
    new AvatarServlet(
      AvatarStore(new TestFileStore(storeProps.fsProcessedBucket), new TestKVStore(storeProps.kvTable), storeProps),
      new TestPublisher,
      avatarServletProps
    ),
    "/*"
  )

  test("Healthcheck should return OK") {
    checkGetOk("/service/healthcheck", _.body == "OK", Nil, Map("Authorization" -> ""))
  }

  test("Error if no Authorization header") {
    checkGetError("/avatars", 401, _.message.startsWith("Unable to get API access token."), Map("Authorization" -> ""))
  }

  test("Error if Authorization token invalid") {
    checkGetError("/avatars", 401, _.message.startsWith("Unable to get API access token."), Map("Authorization" -> "Bearer token=bad"))
  }

  test("Get avatars") {
    checkGetAvatars("/avatars")
  }

  test("Get avatars by status") {
    val statuses = Set(Inactive, Pending, Approved, Rejected)
    statuses.foreach(s => checkGetAvatars(s"/avatars?status=${s.asString}", _.data.status == s))
  }

  test("Error response if invalid status") {
    checkGetError("/avatars?status=badStatus", 400, _.message == "Invalid filter parameters")
  }

  test("Get avatar by ID") {
    checkGetAvatar("/avatars/9f51970f-fc24-400a-9ceb-9b347d9b5e5e", _.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e")
  }

  test("Get avatars by user ID") {
    checkGetAvatars(s"/avatars/user/123456", _.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e")
  }

  test("Get active avatar by user ID") {
    checkGetAvatar(s"/avatars/user/123456/active", a => a.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e" && a.data.isActive)
  }

  test("Get personal avatar by user ID") {
    val (userId, cookie) = testCookie
    checkGetAvatar(
      s"/avatars/user/me/active",
      cookie,
      a => a.data.userId == userId && a.data.status == Inactive
    )
  }

  test("Post avatar") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = testCookie

    postAvatar(
      "/avatars",
      file,
      "false",
      userId,
      cookie,
      a => a.data.userId == userId && a.data.status == Pending && !a.data.isActive && !a.data.isSocial
    )
  }

  test("Social Avatar should default to Inactive status") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = testCookie

    postAvatar(
      "/avatars",
      file,
      "true",
      userId,
      cookie,
      a => a.data.userId == userId && a.data.status == Inactive && !a.data.isActive && a.data.isSocial
    )
  }

  test("Error response if bad isSocial parameter") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = testCookie

    postError(
      "/avatars",
      file,
      "facebook",
      userId,
      cookie,
      400,
      _.message == "Invalid isSocial boolean flag"
    )
  }

  test("Put avatar status") {
    put(
      "/avatars/f1d07680-fd11-492c-9bbf-fc996b435590/status",
      Approved,
      a => a.data.status == Approved && a.data.isActive
    )
  }

  test("Error on Avatar not found") {
    checkGetError("/avatars/does-not-exist", 404, _.message == "Avatar not found")
  }

  test("Support CORS") {
    val headers = Map("Origin" -> "http://example.com")
    checkGetOk("/avatars", _.headers("Access-Control-Allow-Origin") == "*", Nil, headers)
  }
}