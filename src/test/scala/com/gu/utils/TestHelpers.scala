package com.gu.utils

import java.io.File

import com.gu.adapters.http.{ErrorResponse, StatusRequest, StatusSerializer}
import com.gu.core.{Avatar, Status}
import org.joda.time.DateTime
import org.json4s.ext.JodaTimeSerializers
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats, Formats}
import org.scalatest.FunSuiteLike
import org.scalatra.test.ClientResponse
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

class TestHelpers extends ScalatraSuite with FunSuiteLike {

  protected implicit val jsonFormats: Formats =
    DefaultFormats +
      new StatusSerializer ++
      JodaTimeSerializers.all

  def getOk(
    uri: String,
    p: ClientResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()): Unit = {

    get(uri, params, headers) {
      status should equal (200)
      p(response)
    }
  }

  def getAvatars(
    uri: String,
    p: Avatar => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()) = {

    get(uri, params, headers) {
      status should equal(200)
      val avatars = read[List[Avatar]](body)
      avatars.forall(p) should be (true)
    }
  }

  def getAvatars(uri: String): Unit = getAvatars(uri, _ => true)

  def getAvatar(
    uri: String,
    p: Avatar => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()): Unit = {

    get(uri, params, headers) {
      status should equal(200)
      val avatar = read[Avatar](body)
      p(avatar) should be (true)
    }
  }

  def getAvatar(uri: String): Unit = getAvatar(uri, _ => true)

  def getAvatar(uri: String, guuCookie: String, p: Avatar => Boolean): Unit = {
    getAvatar(uri, p, Nil, Map("Cookie" -> ("GU_U=" + guuCookie)))
  }

  def getError(uri: String, code: Int, p: ErrorResponse => Boolean): Unit = {
    get(uri) {
      status should equal(code)
      val error = read[ErrorResponse](body)
      p(error) should be (true)
    }
  }

  def postAvatar(
    uri: String,
    file: File,
    userId: Int,
    guuCookie: String,
    p: Avatar => Boolean): Unit = {

    post("/avatars", Nil, List("image" -> file), Map("Cookie" -> ("GU_U=" + guuCookie))) {
      status should equal(201)
      val avatar = read[Avatar](body)
      p(avatar) should be (true)
      getAvatar(s"/avatars/${avatar.id}", p)
    }
  }
  def postMigratedAvatar(
    endpointUri: String,
    image: String,
    userId: Int,
    processedImage: String,
    isSocial: Boolean,
    originalFilename: String,
    createdAt: DateTime,
    p: Avatar => Boolean): Unit = {

   val createdAtString = "2015-05-14T15:35:09Z"

    val json =
      ("userId" -> userId) ~
      ("image" -> image) ~
      ("processedImage" -> processedImage) ~
      ("createdAt" -> createdAtString) ~
      ("isSocial" -> isSocial) ~
      ("originalFilename" -> originalFilename)

    post(endpointUri, (compact(render(json))).getBytes, Map("Content-type" -> ("application/json"))) {
      status should equal(201)

      val avatar = read[Avatar](body)
      p(avatar) should be (true)
      getAvatar(s"/avatars/${avatar.id}", p)
    }
  }

  def put(uri: String, toStatus: Status, p: Avatar => Boolean): Unit = {
    val sr = StatusRequest(toStatus)

    put(uri, write(sr)) {
      status should equal(200)
      val avatar = read[Avatar](body)
      p(avatar) should be (true)
      getAvatar(s"/avatars/${avatar.id}", p)
    }
  }
}
