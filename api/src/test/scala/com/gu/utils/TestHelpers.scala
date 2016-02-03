package com.gu.utils

import java.io.File

import com.gu.adapters.http._
import com.gu.core.models.Status
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization._
import org.scalatest.FunSuiteLike
import org.scalatra.test.ClientResponse
import org.scalatra.test.scalatest.ScalatraSuite

trait TestHelpers extends ScalatraSuite with FunSuiteLike {

  protected def apiKey: String
  protected def apiUrl: String

  lazy val authHeader = Map("Authorization" -> ("Bearer token=" + apiKey))

  protected implicit val jsonFormats = JsonFormats.all

  def checkGetOk(
    uri: String,
    p: ClientResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ): Unit = {

    get(uri, params, authHeader ++ headers) {
      status should equal(200)
      p(response)
    }
  }

  def checkGetAvatars(
    uri: String,
    p: AvatarResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ) = {

    get(uri, params, authHeader ++ headers) {
      status should equal(200)
      val avatars = read[AvatarsResponse](body).data
      avatars.forall(p) should be(true)
    }
  }

  def checkGetAvatars(uri: String): Unit = checkGetAvatars(uri, _ => true)

  def checkGetAvatar(
    uri: String,
    p: AvatarResponse => Boolean,
    params: List[(String, String)] = Nil,
    headers: Map[String, String] = Map()
  ): Unit = {

    get(uri, params, authHeader ++ headers) {
      status should equal(200)
      val avatar = read[AvatarResponse](body)
      avatar.uri should be(Some(apiUrl + "/avatars/" + avatar.data.id))
      assert(p(avatar), "The avatar did not satisfy the supplied conditions. Avatar: " + avatar)
    }
  }

  def checkGetAvatar(uri: String): Unit = checkGetAvatar(uri, _ => true)

  def checkGetAvatar(uri: String, cookie: String, p: AvatarResponse => Boolean): Unit = {
    checkGetAvatar(uri, p, Nil, Map("Cookie" -> s"SC_GU_U=$cookie;"))
  }

  def checkGetError(
    uri: String,
    code: Int,
    p: ErrorResponse => Boolean,
    headers: Map[String, String] = Map()
  ): Unit = {
    get(uri, Nil, authHeader ++ headers) {
      status should equal(code)
      val error = read[ErrorResponse](body)
      p(error) should be(true)
    }
  }

  def postAvatar(
    uri: String,
    file: File,
    isSocial: String,
    userId: Int,
    cookie: String,
    p: AvatarResponse => Boolean
  ): Unit = {

    post("/avatars", Map("isSocial" -> isSocial), List("file" -> file), Map("Cookie" -> s"SC_GU_U=$cookie;")) {
      status should equal(201)
      val avatar = read[AvatarResponse](body)
      p(avatar) should be(true)
      checkGetAvatar(s"/avatars/${avatar.data.id}", p)
    }
  }

  def postError(
    uri: String,
    file: File,
    isSocial: String,
    userId: Int,
    cookie: String,
    code: Int,
    p: ErrorResponse => Boolean
  ): Unit = {
    post(uri, Map("isSocial" -> isSocial), List("file" -> file), Map("Cookie" -> s"SC_GU_U=$cookie;")) {
      status should equal(code)
      val error = read[ErrorResponse](body)
      p(error) should be(true)
    }
  }

  def put(uri: String, toStatus: Status, p: AvatarResponse => Boolean): Unit = {
    val sr = StatusRequest(toStatus)

    put(uri, write(sr), authHeader) {
      status should equal(200)
      val avatar = read[AvatarResponse](body)
      p(avatar) should be(true)
      checkGetAvatar(s"/avatars/${avatar.data.id}", p)
    }
  }
}