package com.gu.adapters.store

import java.net.URL

import com.gu.adapters.http.TestCookie
import com.gu.adapters.store.TestStoreHelpers.path
import com.gu.core.models._
import com.gu.core.store.{DeleteResponse, FileStore, KVStore, QueryResponse}
import com.gu.core._
import com.gu.core.utils.KVLocationFromID
import Errors.avatarNotFound
import org.joda.time.{DateTime, DateTimeZone}
import software.amazon.awssdk.services.s3.model.PutObjectRequest

object TestStoreHelpers {
  def path(a: String, b: String): String = a + "/" + b
}

class TestFileStore(s3ProcessedBucket: String) extends FileStore {

  private[store] var files: Map[String, String] = {
    val id = "f1d07680-fd11-492c-9bbf-fc996b435590"
    Map(s"$s3ProcessedBucket/${KVLocationFromID(id)}" -> "some-file")
  }

  def exists(bucket: String, key: String): Boolean = {
    files.get(TestStoreHelpers.path(bucket, key)).nonEmpty
  }

  def get(bucket: String, key: String): Option[String] = {
    files.get(TestStoreHelpers.path(bucket, key))
  }

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): Either[models.Error, Unit] = {

    val oldPath = path(fromBucket, fromKey)
    val newPath = path(toBucket, toKey)
    val file = files(oldPath)
    files += newPath -> file
    Right(())
  }

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: PutObjectRequest.Builder
  ): Either[models.Error, Unit] = {

    files += path(bucket, key) -> file.map(_.toChar).mkString
    Right(())
  }

  def presignedUrl(
    bucket: String,
    key: String
  ): Either[models.Error, URL] = {
    Right(new URL("http://some-url/"))
  }

  def delete(bucket: String, keys: String*): Either[models.Error, Unit] = {
    val paths = keys.map(key => path(bucket, key))

    files = files.filterKeys(key => !paths.contains(key)).toMap

    Right(())
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

  def get(table: String, id: String): Either[models.Error, Avatar] = {
    docs.get(path(table, id)).toRight(avatarNotFound(List(s"avatar with ID '$id' does not exist")))
  }

  def getKey(table: String, id: String): Option[Avatar] = docs.get(path(table, id))

  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): Either[models.Error, QueryResponse] = {
    Right(QueryResponse(docs.values.filter(_.userId == userId).toList, hasMore = false))
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): Either[models.Error, QueryResponse] = {
    Right(QueryResponse(docs.values.filter(_.status == status).toList, hasMore = false))
  }

  def put(table: String, avatar: Avatar): Either[models.Error, Avatar] = {
    docs += path(table, avatar.id) -> avatar
    Right(avatar)
  }

  def update(table: String, id: String, status: Status, isActive: Boolean): Either[models.Error, Avatar] = {
    val p = path(table, id)
    val old = docs.get(p).toRight(avatarNotFound(List(s"$id missing")))
    old map { a =>
      val updated = a.copy(status = status, isActive = isActive)
      docs += p -> updated
      updated
    }
  }

  def delete(table: String, ids: List[String]): Either[Error, DeleteResponse] = {
    docs = docs.filterKeys(id => !ids.map(k => s"$dynamoTable/${k}").contains(id)).toMap
    Right(DeleteResponse(ids))
  }
}
