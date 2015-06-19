package com.gu.adapters.utils

import com.gu.adapters.utils
import org.scalatest.{ Matchers, FunSuite }

class TestS3FoldersFromId extends FunSuite with Matchers {

  test("Make S3 folder") {
    val avatarId = "20364a44-a914-4edd-a7de-4aeb621a2ab5"
    utils.S3FoldersFromId(avatarId) should be("""2/0/3/6""")
  }
}