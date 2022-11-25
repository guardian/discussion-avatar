package com.gu.core.utils

import com.gu.core.utils
import org.scalatest.{Matchers, FunSuite}

class KVLocationFromId extends FunSuite with Matchers {

  test("Make key-value store location from ID") {
    val avatarId = "20364a44-a914-4edd-a7de-4aeb621a2ab5"
    utils.KVLocationFromID(avatarId) should be(s"2/0/3/6/$avatarId")
  }
}

class UnicodeEscapedFromFilename extends FunSuite with Matchers {

  test("Make Unicode escaped version from filename") {
    val originalFilename = "üe.JPG"
    utils.EscapedUnicode(originalFilename) should be("\\u00FCe.JPG")
    val originalFilename2 = "helloworld.JPG"
    utils.EscapedUnicode(originalFilename2) should be("helloworld.JPG")
    //Arabic
    val originalFilename3 = "\u063A.JPG"
    utils.EscapedUnicode(originalFilename3) should be("\\u063A.JPG")
  }
}

