package com.gu.adapters.http

import com.gu.adapters.store.AvatarStore
import com.gu.entities.Avatar
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.FunSuiteLike
import org.scalatra.test.scalatest.ScalatraSuite

import scala.util.Try

class AvatarServletTests extends ScalatraSuite with FunSuiteLike {

  implicit val swagger = new AvatarSwagger
  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer

  addServlet(new AvatarServlet(AvatarStore), "/*")

  test("Management healthcheck should return OK") {
    get("/management/healthcheck") {
      status should equal (200)
      body should include ("OK")
    }
  }

  test("Return a list of avatars") {
    get("/avatars") {
      status should equal (200)
      Try(read[AvatarList](body)).isSuccess should be (true)
    }
  }

  test("Return an avatar") {
    get("/avatars/123") {
      status should equal (200)
      Try(read[Avatar](body)).isSuccess should be (true)
    }
  }

  test("Return active avatar for a user") {
    get("/avatars/user/123/active") {
      status should equal (200)
      Try(read[Avatar](body)).isSuccess should be (true)
    }
  }

}
