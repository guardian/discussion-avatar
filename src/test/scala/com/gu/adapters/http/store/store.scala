package com.gu.adapters.http.store

import java.net.URL

import com.amazonaws.services.s3.model.{ ObjectMetadata }
import com.gu.adapters.http.store.TestStoreHelpers.path
import com.gu.adapters.store.{ FileStore, KVStore, QueryResponse }
import com.gu.adapters.utils.S3FoldersFromId
import com.gu.core.Errors.avatarNotFound
import com.gu.core._
import org.joda.time.{ DateTimeZone, DateTime }

import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

object TestStoreHelpers {
  def path(a: String, b: String): String = a + "/" + b
  def processedId = "f1d07680-fd11-492c-9bbf-fc996b435590"
}

class TestFileStore extends FileStore {
  private[this] var files: Map[String, String] = Map(
    (s"${Config.s3ProcessedBucket}/${S3FoldersFromId(TestStoreHelpers.processedId)}/${TestStoreHelpers.processedId}" -> "some-file")
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

  def presignedUrl(
    bucket: String,
    key: String,
    expiration: DateTime = DateTime.now(DateTimeZone.UTC).plusMinutes(20)
  ): Error \/ URL = {
    new URL("http://some-url/").right
  }

  def delete(bucket: String, key: String): Error \/ Unit = {
    files -= path(bucket, key)
    ().right
  }
}

class TestKVStore extends KVStore {
  private[this] var docs: Map[String, Avatar] = Map(
    Config.dynamoTable + "/9f51970f-fc24-400a-9ceb-9b347d9b5e5e" -> Avatar(
      "9f51970f-fc24-400a-9ceb-9b347d9b5e5e",
      "http://avatar-url-1",
      123456,
      "foo.gif",
      "http://avatar-raw-url1",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = true
    ),
    Config.dynamoTable + "/5aa5aa52-ee78-4319-8fa0-93bfd1dc204b" -> Avatar(
      "5aa5aa52-ee78-4319-8fa0-93bfd1dc204b",
      "http://avatar-url-2",
      234567,
      "bar.gif",
      "http://avatar-raw-url2",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    Config.dynamoTable + "/f1d07680-fd11-492c-9bbf-fc996b435590" -> Avatar(
      "f1d07680-fd11-492c-9bbf-fc996b435590",
      "http://avatar-url-3",
      345678,
      "gra.gif",
      "http://avatar-raw-url3",
      Pending,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    Config.dynamoTable + "/6cdab5ef-e93c-4cc3-b761-dc97f66ae257" -> Avatar(
      "6cdab5ef-e93c-4cc3-b761-dc97f66ae257",
      "http://avatar-url-4",
      21801602,
      "gra.gif",
      "http://avatar-raw-url-4",
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

  def query(table: String, index: String, userId: Int, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.userId == userId).toList, hasMore = false).right
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.status == status).toList, hasMore = false).right
  }

  def put(table: String, avatar: Avatar): Error \/ Avatar = {
    docs += path(table, avatar.id) -> avatar
    avatar.right
  }

  def update(table: String, id: String, status: Status, isActive: Boolean): Error \/ Avatar = {
    val p = path(table, id)
    val old = docs.get(p).toRightDisjunction(avatarNotFound(NonEmptyList(s"$id missing")))
    old map { a =>
      val updated = a.copy(status = status, isActive = isActive)
      docs += p -> updated
      updated
    }
  }
}