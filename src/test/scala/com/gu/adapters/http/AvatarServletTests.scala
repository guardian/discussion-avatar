package com.gu.adapters.http

import java.io.File

import com.gu.adapters.http.store.{TestFileStore, TestKVStore}
import com.gu.adapters.store.AvatarStore
import com.gu.core._
import com.gu.utils.TestHelpers
import org.joda.time.DateTime

class AvatarServletTests extends TestHelpers {

  implicit val swagger = new AvatarSwagger

  addServlet(
    new AvatarServlet(
      AvatarStore(new TestFileStore, new TestKVStore),
      Config.cookieDecoder),
    "/*")

  test("Healthcheck should return OK") {
    getOk("/service/healthcheck", _.body == "OK")
  }

  test("Get avatars") {
    getAvatars("/avatars")
  }

  test("Get avatars by status") {
    val statuses = Set(Inactive, Pending, Approved, Rejected)
    statuses.foreach(s => getAvatars(s"/avatars?status=${s.asString}", _.status == s))
  }

  test("Error response if invalid status") {
    getError("/avatars?status=badStatus", 400, _.message == "Invalid filter parameters")
  }

  test("Get avatar by ID") {
    getAvatar("/avatars/123", _.id == "123")
  }

  test("Get avatars by user ID") {
    getAvatars(s"/avatars/user/123", _.id == "123")
  }

  test("Get active avatar by user ID") {
    getAvatar(s"/avatars/user/123/active", a => a.id == "123" && a.isActive)
  }

  test("Get personal avatar by user ID") {
    val (userId, cookie) = Config.preProdCookie
    getAvatar(
      s"/avatars/user/me/active",
      cookie,
      a => a.userId == userId && a.status == Inactive)
  }

  test("Post migrated avatar") {
    val image = new File("src/test/resources/avatar.gif").toURI.toString
    val processedImage = new File("src/test/resources/avatar.gif").toURI.toString
    val userId=991

    postMigratedAvatar (
      "/migratedAvatar",
      image,
      userId,
      processedImage,
      true,
      "original.gif",
      new DateTime(),
      a => a.userId == userId && a.status == Approved)
  }

  test("Post avatar") {
    val file = new File("src/test/resources/avatar.gif")
    val (userId, cookie) = Config.preProdCookie

    postAvatar(
      "/avatars",
      file,
      userId,
      cookie,
      a => a.userId == userId && a.status == Pending)
  }

  test("Put avatar status") {
    put("/avatars/345/status", Approved, _.status == Approved)
  }

  test("Error on Avatar not found") {
    getError("/avatars/does-not-exist", 404, _.message == "Avatar not found")
  }

  test("Support CORS") {
    val headers = Map("Origin" -> "http://example.com")
    getOk("/avatars", _.headers("Access-Control-Allow-Origin") == "*", Nil, headers)
  }
}