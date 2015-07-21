package com.gu.adapters.http

import java.io.{ BufferedInputStream, File, FileInputStream }

import com.gu.adapters.utils.IO.readBytesAndCloseInputStream
import org.scalatest.{ FunSuite, Matchers }

class ImageValidatorTests extends FunSuite with Matchers {

  def test(path: String, isValid: Boolean): Unit = {
    val file = new File(path)
    val image = readBytesAndCloseInputStream(new FileInputStream(file)) getOrElse fail("Could not load image!")
    ImageValidator.validate(image).isRight should be(isValid)
  }

  test("Reject unsupported file types") {
    val files = Map(
      "src/test/resources/avatar.gif" -> true,
      "src/test/resources/avatar.png" -> true,
      "src/test/resources/avatar.jpg" -> true,
      "src/test/resources/avatar-gif" -> true,
      "src/test/resources/avatar.svg" -> false
    )

    files foreach { case (path, isValid) => test(path, isValid) }
  }

  test("Reject animated gifs") {
    test("src/test/resources/animated.gif", false)
  }
}
