package com.gu

import org.scalatest.FunSuiteLike
import org.scalatra.test.scalatest.ScalatraSuite

class AvatarServletTests extends ScalatraSuite with FunSuiteLike {

  addServlet(classOf[AvatarServlet], "/*")

  test("simple get") {
    get("/management/healthcheck") {
      status should equal (200)
      body should include ("OK")
    }
  }

}
