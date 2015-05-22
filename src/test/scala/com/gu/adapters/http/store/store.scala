package com.gu.adapters.http.store

import java.util.UUID

import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.adapters.http.store.TestStoreHelpers.path
import com.gu.adapters.store.{ FileStore, KVStore, QueryResponse }
import com.gu.core.Errors.avatarNotFound
import com.gu.core._
import org.joda.time.DateTime

import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object TestStoreHelpers {
  def path(a: String, b: String): String = a + "/" + b
}

class TestFileStore extends FileStore {
  private[this] var files: Map[String, String] = Map(
    (Config.s3PrivateBucket + "/avatars/345") -> "some-file"
  )

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): Error \/ Unit = {

    val oldPath = path(fromBucket, fromKey)
    val newPath = path(toBucket, toKey)
    val file = files(oldPath)
    files -= oldPath
    files += newPath -> file
    ().right
  }

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: ObjectMetadata
  ): Error \/ Unit = {

    files += path(bucket, key) -> file.toString // TODO FIX
    ().right
  }

  def delete(bucket: String, key: String): Error \/ Unit = {
    files -= path(bucket, key)
    ().right
  }
}

class TestKVStore extends KVStore {
  private[this] var docs: Map[String, Avatar] = Map(
    Config.dynamoTable + "/123" -> Avatar(
      "123",
      "http://avatar-url-1",
      123,
      "foo.gif",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = true
    ),
    Config.dynamoTable + "/234" -> Avatar(
      "234",
      "http://avatar-url-2",
      234,
      "bar.gif",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    Config.dynamoTable + "/345" -> Avatar(
      "345",
      "http://avatar-url-2",
      345,
      "gra.gif",
      Pending,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    Config.dynamoTable + "/456" -> Avatar(
      "456",
      "http://avatar-url-2",
      21801602,
      "gra.gif",
      Inactive,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = false
    )
  )

  def get(table: String, id: String): Error \/ Avatar = {
    docs.get(path(table, id)).toRightDisjunction(avatarNotFound(NonEmptyList(s"$id missing")))
  }

  def query(table: String, index: String, userId: Int, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.userId == userId).toList, hasMore = false).right
  }

  def query(table: String, index: String, status: Status, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.status == status).toList, hasMore = false).right
  }

  def put(table: String, avatar: Avatar): Error \/ Avatar = {
    docs += path(table, avatar.id) -> avatar
    avatar.right
  }

  def update(table: String, id: String, status: Status): Error \/ Avatar = {
    val p = path(table, id)
    val old = docs.get(p).toRightDisjunction(avatarNotFound(NonEmptyList(s"$id missing")))
    old map { a =>
      val updated = a.copy(status = status)
      docs += p -> updated
      updated
    }
  }
}