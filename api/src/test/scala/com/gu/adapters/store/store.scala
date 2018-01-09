package com.gu.adapters.store

import java.net.URL

import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.adapters.http.TestCookie
import com.gu.adapters.store.TestStoreHelpers.path
import com.gu.core.models._
import com.gu.core.store.{DeleteResponse, FileStore, KVStore, QueryResponse}
import com.gu.core._
import com.gu.core.utils.KVLocationFromID
import Errors.avatarNotFound
import org.joda.time.{DateTime, DateTimeZone}

import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/}

object TestStoreHelpers {
  def path(a: String, b: String): String = a + "/" + b
}

class TestFileStore(s3ProcessedBucket: String) extends FileStore {

  private[this] var files: Map[String, String] = {
    val id = "f1d07680-fd11-492c-9bbf-fc996b435590"
    Map(s"$s3ProcessedBucket/${KVLocationFromID(id)}" -> "some-file")
  }

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): models.Error \/ Unit = {

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
  ): models.Error \/ Unit = {

    files += path(bucket, key) -> file.toString
    ().right
  }

  def presignedUrl(
    bucket: String,
    key: String,
    expiration: DateTime = DateTime.now(DateTimeZone.UTC).plusMinutes(20)
  ): models.Error \/ URL = {
    new URL("http://some-url/").right
  }

  def delete(bucket: String, keys: String*): models.Error \/ Unit = {
    val paths = keys.map(key => path(bucket, key))
    files = files.filterKeys(key => !paths.contains(key))

    ().right
  }
}

class TestKVStore(dynamoTable: String) extends KVStore {

  private[this] var docs: Map[String, Avatar] = Map(
    dynamoTable + "/9f51970f-fc24-400a-9ceb-9b347d9b5e5e" -> Avatar(
      "9f51970f-fc24-400a-9ceb-9b347d9b5e5e",
      "http://avatar-url-1",
      "123456",
      "foo.gif",
      "http://avatar-raw-url1",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = true
    ),
    dynamoTable + "/5aa5aa52-ee78-4319-8fa0-93bfd1dc204b" -> Avatar(
      "5aa5aa52-ee78-4319-8fa0-93bfd1dc204b",
      "http://avatar-url-2",
      "234567",
      "bar.gif",
      "http://avatar-raw-url2",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    dynamoTable + "/f1d07680-fd11-492c-9bbf-fc996b435590" -> Avatar(
      "f1d07680-fd11-492c-9bbf-fc996b435590",
      "http://avatar-url-3",
      "345678",
      "gra.gif",
      "http://avatar-raw-url3",
      Pending,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    ),
    dynamoTable + "/6cdab5ef-e93c-4cc3-b761-dc97f66ae257" -> Avatar(
      "6cdab5ef-e93c-4cc3-b761-dc97f66ae257",
      "http://avatar-url-4",
      TestCookie.userId,
      "gra.gif",
      "http://avatar-raw-url-4",
      Inactive,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = false
    )
  )

  def get(table: String, id: String): models.Error \/ Avatar = {
    docs.get(path(table, id)).toRightDisjunction(avatarNotFound(NonEmptyList(s"avatar with ID '$id' does not exist")))
  }

  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): models.Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.userId == userId).toList, hasMore = false).right
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): models.Error \/ QueryResponse = {
    QueryResponse(docs.values.filter(_.status == status).toList, hasMore = false).right
  }

  def put(table: String, avatar: Avatar): models.Error \/ Avatar = {
    docs += path(table, avatar.id) -> avatar
    avatar.right
  }

  def update(table: String, id: String, status: Status, isActive: Boolean): models.Error \/ Avatar = {
    val p = path(table, id)
    val old = docs.get(p).toRightDisjunction(avatarNotFound(NonEmptyList(s"$id missing")))
    old map { a =>
      val updated = a.copy(status = status, isActive = isActive)
      docs += p -> updated
      updated
    }
  }

  def delete(table: String, ids: List[String]): Error \/ DeleteResponse = {
    docs = docs.filterKeys(id => !ids.contains(id))
    DeleteResponse(ids).right
  }
}
