package com.gu.adapters.http

import com.gu.adapters.http.store.{TestFileStore, TestKVStore}
import com.gu.adapters.store.AvatarStore
import com.gu.core._
import com.gu.utils.TestHelpers

class AvatarServletTests extends TestHelpers {

  implicit val swagger = new AvatarSwagger

  addServlet(new AvatarServlet(AvatarStore(new TestFileStore, new TestKVStore)), "/*")

  test("Healthcheck should return OK") {
    get("/service/healthcheck") {
      status should equal (200)
      body should include ("OK")
    }
  }

  test("Get avatars") {
    getAvatars("/avatars")
  }

  test("Get avatars by status") {
    val statuses = Set(Inactive, Pending, Approved, Rejected)

    for (status <- statuses) {
      getAvatars(s"/avatars?status=${status.asString}", _.status == status)
    }

    getAvatars(s"/avatars?status=${All.asString}")
  }

  test("Error response if invalid status") {
    getError(
      "/avatars?status=badStatus",
      400,
      _.message == "Invalid filter parameters")
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

  test("Post avatar") {
    pending
  }

  test("Put avatar status") {
    pending
  }
}
