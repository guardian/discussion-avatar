package com.gu.utils

import java.io.File

import com.gu.adapters.http._
import com.gu.adapters.utils.ISODateFormatter
import org.joda.time.{ DateTimeZone, DateTime }
import com.gu.core.{ Config, Status }
import org.json4s.native.Serialization._
import org.scalatest.FunSuiteLike
import org.scalatra.test.ClientResponse
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

class TestHelpers extends ScalatraSuite with FunSuiteLike {

  protected implicit val jsonFormats = JsonFormats.all

  def getOk(
    uri: String,
    p: ClientResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ): Unit = {

    get(uri, params, headers) {
      status should equal(200)
      p(response)
    }
  }

  def getAvatars(
    uri: String,
    p: AvatarResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ) = {

    get(uri, params, headers) {
      status should equal(200)
      val avatars = read[AvatarsResponse](body).data
      avatars.forall(p) should be(true)
    }
  }

  def getAvatars(uri: String): Unit = getAvatars(uri, _ => true)

  def getAvatar(
    uri: String,
    p: AvatarResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ): Unit = {

    get(uri, params, headers) {
      status should equal(200)
      val avatar = read[AvatarResponse](body)
      avatar.uri should be(Some(Config.apiUrl + "/avatars/" + avatar.data.id))
      p(avatar) should be(true)
    }
  }

  def getAvatar(uri: String): Unit = getAvatar(uri, _ => true)

  def getAvatar(uri: String, guuCookie: String, p: AvatarResponse => Boolean): Unit = {
    getAvatar(uri, p, Nil, Map("Authorization" -> ("Bearer " + guuCookie)))
  }

  def getError(uri: String, code: Int, p: ErrorResponse => Boolean): Unit = {
    get(uri) {
      status should equal(code)
      val error = read[ErrorResponse](body)
      p(error) should be(true)
    }
  }

  def postAvatar(
    uri: String,
    file: File,
    userId: Int,
    guuCookie: String,
    p: AvatarResponse => Boolean
  ): Unit = {

    post("/avatars", Nil, List("file" -> file), Map("Authorization" -> ("Bearer " + guuCookie))) {
      status should equal(201)
      val avatar = read[AvatarResponse](body)
      p(avatar) should be(true)
      getAvatar(s"/avatars/${avatar.data.id}", p)
    }
  }

  def postMigratedAvatar(expectedStatus: Int)(
    endpointUri: String,
    image: String,
    userId: Int,
    processedImage: String,
    isSocial: Boolean,
    originalFilename: String,
    createdAt: DateTime,
    p: AvatarResponse => Boolean
  ): Unit = {

    // TODO: Non UTC dates with offset are rejected, fix parser to be more lenient
    val utcDate = createdAt.toDateTime(DateTimeZone.UTC)

    val json =
      ("userId" -> userId) ~
        ("image" -> image) ~
        ("processedImage" -> processedImage) ~
        ("createdAt" -> ISODateFormatter.print(utcDate)) ~
        ("isSocial" -> isSocial) ~
        ("originalFilename" -> originalFilename)

    post(endpointUri, (compact(render(json))).getBytes, Map("Content-type" -> ("application/json"))) {
      status should equal(expectedStatus)

      if (status == 201) {
        val avatar = read[AvatarResponse](body)
        p(avatar) should be(true)
        getAvatar(s"/avatars/${avatar.data.id}", p)
      }
    }
  }

  def put(uri: String, toStatus: Status, p: AvatarResponse => Boolean): Unit = {
    val sr = StatusRequest(toStatus)

    put(uri, write(sr)) {
      status should equal(200)
      val avatar = read[AvatarResponse](body)
      p(avatar) should be(true)
      getAvatar(s"/avatars/${avatar.data.id}", p)
    }
  }
}
