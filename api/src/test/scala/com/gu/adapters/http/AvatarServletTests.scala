package com.gu.adapters.http

import java.io.File

import com.gu.adapters.config.Config
import com.gu.adapters.http.store.{ TestFileStore, TestKVStore }
import com.gu.adapters.notifications.TestPublisher
import com.gu.adapters.store.AvatarStore
import com.gu.core._
import com.gu.utils.TestHelpers

class AvatarServletTests extends TestHelpers {

  implicit val swagger = new AvatarSwagger

  addServlet(
    new AvatarServlet(
      AvatarStore(new TestFileStore, new TestKVStore),
      Config.cookieDecoder,
      new TestPublisher
    ),
    "/*"
  )

  test("Healthcheck should return OK") {
    getOk("/service/healthcheck", _.body == "OK", Nil, Map("Authorization" -> ""))
  }

  test("Error if no Authorization header") {
    getError("/avatars", 401, _.message.startsWith("Unable to get API access token."), Map("Authorization" -> ""))
  }

  test("Error if Authorization token invalid") {
    getError("/avatars", 401, _.message.startsWith("Unable to get API access token."), Map("Authorization" -> "Bearer token=bad"))
  }

  test("Get avatars") {
    getAvatars("/avatars")
  }

  test("Get avatars by status") {
    val statuses = Set(Inactive, Pending, Approved, Rejected)
    statuses.foreach(s => getAvatars(s"/avatars?status=${s.asString}", _.data.status == s))
  }

  test("Error response if invalid status") {
    getError("/avatars?status=badStatus", 400, _.message == "Invalid filter parameters")
  }

  test("Get avatar by ID") {
    getAvatar("/avatars/9f51970f-fc24-400a-9ceb-9b347d9b5e5e", _.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e")
  }

  test("Get avatars by user ID") {
    getAvatars(s"/avatars/user/123456", _.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e")
  }

  test("Get active avatar by user ID") {
    getAvatar(s"/avatars/user/123456/active", a => a.data.id == "9f51970f-fc24-400a-9ceb-9b347d9b5e5e" && a.data.isActive)
  }

  test("Get personal avatar by user ID") {
    val (userId, cookie) = Config.preProdCookie
    getAvatar(
      s"/avatars/user/me/active",
      cookie,
      a => a.data.userId == userId && a.data.status == Inactive
    )
  }

  test("Post migrated avatar for non-social user") {
    val image = new File("src/test/resources/avatar.gif").toURI.toString
    val processedImage = new File("src/test/resources/avatar.gif").toURI.toString
    val userId = 991

    postMigratedAvatar(201)(
      "/migrateAvatar",
      userId,
      image,
      processedImage,
      "original.gif",
      "approved",
      "2015-12-09T17:54:06Z",
      isSocial = false,
      a => a.data.userId == userId && a.data.status == Approved && a.data.isActive && !a.data.isSocial
    )
  }

  test("Post migrated avatar for social user") {
    val image = new File("src/test/resources/avatar.gif").toURI.toString
    val processedImage = new File("src/test/resources/avatar.gif").toURI.toString
    val userId = 992

    postMigratedAvatar(201)(
      "/migrateAvatar",
      userId,
      image,
      processedImage,
      "original.gif",
      "inactive",
      "2015-12-09T17:54:06Z",
      isSocial = true,
      a => a.data.userId == userId && a.data.status == Inactive && !a.data.isActive && a.data.isSocial
    )
  }

  test("Reject invalid migrated avatar mime-type") {
    val image = new File("src/test/resources/avatar.svg").toURI.toString
    val processedImage = new File("src/test/resources/avatar.gif").toURI.toString
    val userId = 993

    postMigratedAvatar(400)(
      "/migrateAvatar",
      userId,
      image,
      processedImage,
      "avatar.svg",
      "approved",
      "2015-12-09T17:54:06Z",
      isSocial = true,
      a => a.data.userId == userId && a.data.status == Approved
    )
  }

  test("Reject migration of already existing user") {

    val image = new File("src/test/resources/avatar.gif").toURI.toString
    val processedImage = new File("src/test/resources/avatar.gif").toURI.toString
    val userId = 999

    postMigratedAvatar(201)(
      "/migrateAvatar",
      userId,
      image,
      processedImage,
      "original.gif",
      "approved",
      "2015-12-09T17:54:06Z",
      isSocial = false,
      a => a.data.userId == userId && a.data.status == Approved && !a.data.isSocial
    )

    postMigratedAvatar(409)(
      "/migrateAvatar",
      userId,
      image,
      processedImage,
      "original.gif",
      "approved",
      "2015-12-09T17:54:06Z",
      isSocial = true,
      a => a.data.userId == userId && a.data.status == Approved
    )
  }

  test("Post avatar") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = Config.preProdCookie

    postAvatar(
      "/avatars",
      file,
      "true",
      userId,
      cookie,
      a => a.data.userId == userId && a.data.status == Pending && !a.data.isActive && a.data.isSocial
    )
  }

  test("Error response if bad isSocial parameter") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = Config.preProdCookie

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
    getError("/avatars/does-not-exist", 404, _.message == "Avatar not found")
  }

  test("Support CORS") {
    val headers = Map("Origin" -> "http://example.com")
    getOk("/avatars", _.headers("Access-Control-Allow-Origin") == "*", Nil, headers)
  }
}