package com.gu.utils

import com.gu.adapters.http.StatusSerializer
import com.gu.entities.Avatar
import org.scalatest.FunSuiteLike
import org.scalatra.test.scalatest.ScalatraSuite
import org.json4s.native.Serialization.read
import org.json4s.{DefaultFormats, Formats}

import scala.util.Try

class TestHelpers extends ScalatraSuite with FunSuiteLike {

  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer

  def getAvatars(uri: String, p: Avatar => Boolean, params: List[(String, String)] = Nil, headers: Map[String, String] = Map()) = {
    get(uri, params, headers) {
      status should equal(200)
      val avatars = Try(read[List[Avatar]](body)).get
      avatars.forall(p) should be 'true
    }
  }

  def getAvatars(uri: String) = getAvatars(uri, _ => true)

  def getAvatar(uri: String, p: Avatar => Boolean, params: List[(String, String)] = Nil, headers: Map[String, String] = Map()) = {
    get(uri, params, headers) {
      status should equal(200)
      val avatar = Try(read[Avatar](body)).get
      p(avatar) should be 'true
    }
  }

  def getAvatar(uri: String) = getAvatar(uri, _ => true)
}
