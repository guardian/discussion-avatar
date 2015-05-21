package com.gu.adapters.store

import java.io.{ByteArrayInputStream}
import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{QuerySpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.adapters.http.Filters
import com.gu.adapters.utils.Attempt.io
import com.gu.adapters.utils.ISODateFormatter
import com.gu.core.Errors._
import com.gu.core.{Config, _}
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/}

case class QueryResponse(
  avatars: List[Avatar],
  hasMore: Boolean
)

trait KVStore {
  def get(table: String, id: String): Error \/ Avatar
  def query(table: String, index: String, userId: Int, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse
  def query(table: String, index: String, status: Status, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse
  def put(table: String, avatar: Avatar): Error \/ Avatar
  def update(table: String, id: String, status: Status): Error \/ Avatar
}

case class Dynamo(db: DynamoDB) extends KVStore {

  val apiUrl = Config.apiUrl
  val pageSize = Config.pageSize
  val privateBucket = Config.s3PrivateBucket

  def asAvatar(item: Item, baseUrl: String, avatarUrl: String): Avatar = {
    val id = item.getString("AvatarId")

    Avatar(
      id = id,
      avatarUrl = s"http://$avatarUrl/avatars/$id",
      userId = item.getString("UserId").toInt,
      originalFilename = item.getString("OriginalFilename"),
      status = Status(item.getString("Status")),
      createdAt = ISODateFormatter.parse(item.getString("CreatedAt")),
      lastModified = ISODateFormatter.parse(item.getString("LastModified")),
      isSocial = item.getString("IsSocial").toBoolean,
      isActive = item.getString("IsActive").toBoolean
    )
  }

  def get(table: String, id: String): Error \/ Avatar = {
    io(db.getTable(table).getItem("AvatarId", id))
      .ensure(avatarNotFound(NonEmptyList(s"avatar with ID: $id not found")))(_ != null) // getItem can return null alas
      .map(item => asAvatar(item, apiUrl, privateBucket))
  }

  def query[A](
    table: String,
    index: String,
    key: String,
    value: A,
    since: Option[UUID] = None,
    until: Option[UUID] = None): Error \/ QueryResponse = {

    val spec = new QuerySpec()
      .withHashKey(key, value)
      .withMaxResultSize(pageSize)

    List(since, until).flatten.map(cursor => spec.withExclusiveStartKey(key, value, "AvatarId", cursor.toString))

    until.map(_ => spec.withScanIndexForward(false))

    val result = io(db.getTable(table).getIndex(index).query(spec))

    for {
      pages <- result.map(_.pages.asScala.toList)
      qr <- result.map(_.getLastLowLevelResult.getQueryResult)
    } yield {
      val items = pages.map(_.asScala).flatten
        .map(item => asAvatar(item, apiUrl, privateBucket))
      val orderedItems = until.map(_ => items.reverse).getOrElse(items)
      QueryResponse(orderedItems, qr.getLastEvaluatedKey != null)
    }
  }

  def query(table: String, index: String, userId: Int, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse = {
    query(table, index, "UserId", userId, since, until)
  }

  def query(table: String, index: String, status: Status, since: Option[UUID], until: Option[UUID]): Error \/ QueryResponse = {
    query(table, index, "Status", status.asString, since, until)
  }

  def put(table: String, avatar: Avatar): Error \/ Avatar = {
    val item = new Item()
      .withPrimaryKey("AvatarId", avatar.id)
      .withNumber("UserId", avatar.userId)
      .withString("OriginalFilename", avatar.originalFilename)
      .withString("Status", avatar.status.asString)
      .withString("CreatedAt", ISODateFormatter.print(avatar.createdAt))
      .withString("LastModified", ISODateFormatter.print(avatar.lastModified))
      .withBoolean("IsSocial", avatar.isSocial)
      .withBoolean("IsActive", avatar.isActive)

    io(db.getTable(table).putItem(item)) map (_ => avatar)
  }

  def update(table: String, id: String, status: Status): Error \/ Avatar = {
    val spec = new UpdateItemSpec()
      .withPrimaryKey("AvatarId", id)
      .withAttributeUpdate(new AttributeUpdate("Status").put(status.asString))
      .withReturnValues(ReturnValue.ALL_NEW)
    val item = io(db.getTable(table).updateItem(spec)).map(_.getItem)
    item.map(i => asAvatar(i, apiUrl, privateBucket))
  }
}

object Dynamo {
  def apply(): Dynamo = {
    val client = new AmazonDynamoDBClient(new DefaultAWSCredentialsProviderChain())
    client.setRegion(Region.getRegion(Regions.EU_WEST_1))
    Dynamo(new DynamoDB(client))
  }
}

trait FileStore {
  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String): Error \/ Unit

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: ObjectMetadata): Error \/ Unit

  def delete(bucket: String, key: String): Error \/ Unit
}

case class S3(client: AmazonS3Client) extends FileStore {

  def getMetadata(bucket: String, key: String): Error \/ ObjectMetadata = {
    val request = new GetObjectMetadataRequest(bucket, key)
    client.getObjectMetadata(request).right
  }

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String): Error \/ Unit = {

    val request = new CopyObjectRequest(fromBucket, fromKey, toBucket, toKey)
    io(client.copyObject(request))
  }

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: ObjectMetadata): Error \/ Unit = {

    val inputStream = new ByteArrayInputStream(file)
    metadata.setContentLength(file.length)
    val request = new PutObjectRequest(bucket, key, inputStream, metadata)
    io(client.putObject(request))
  }

  def delete(bucket: String, key: String): Error \/ Unit = {
    val request = new DeleteObjectRequest(bucket, key)
    io(client.deleteObject(request))
  }
}

object S3 {
  def apply(): S3 = {
    val client = new AmazonS3Client(new DefaultAWSCredentialsProviderChain())
    client.setRegion(Region.getRegion(Regions.EU_WEST_1))
    S3(client)
  }
}

case class AvatarStore(fs: FileStore, kvs: KVStore) {

  val apiBaseUrl = Config.apiUrl
  val publicBucket = Config.s3PublicBucket
  val privateBucket = Config.s3PrivateBucket
  val dynamoTable = Config.dynamoTable
  val statusIndex = Config.statusIndex
  val userIndex = Config.userIndex
  
  def get(filters: Filters): \/[Error, FoundAvatars] = {
    for {
      qr <- kvs.query(dynamoTable, statusIndex, filters.status, filters.since, filters.until)
    } yield FoundAvatars(qr. avatars, qr.hasMore)
  }

  def get(id: String): Error \/ FoundAvatar = {
    kvs.get(dynamoTable, id) map FoundAvatar
  }

  def get(user: User): \/[Error, FoundAvatars] = {
    for {
      qr <- kvs.query(
        dynamoTable,
        userIndex,
        user.id,
        None,
        None)
      // avatars <- qr.avatars.map(_.sortWith { case (a, b) => a.lastModified isAfter b.lastModified})
    } yield FoundAvatars(qr.avatars, qr.hasMore)
  }

  def getActive(user: User): Error \/ FoundAvatar = {
    for {
      found <- get(user)
      avatar <- found.body.find(_.isActive)
        .toRightDisjunction(avatarNotFound(NonEmptyList(s"No active avatar found for user: ${user.id}.")))
    } yield FoundAvatar(avatar)
  }

  def getPersonal(user: User): Error \/ FoundAvatar = {
    for {
      found <- get(user)
      avatar <- found.body.find(a => a.isActive || a.status == Inactive)
        .toRightDisjunction(avatarNotFound(NonEmptyList(s"No active avatar found for user: ${user.id}.")))
    } yield FoundAvatar(avatar)
  }

  def userUpload(
    user: User,
    file: Array[Byte],
    originalFilename: String,
    isSocial: Boolean = false): Error \/ CreatedAvatar = {

    val avatarId = UUID.randomUUID.toString
    val now = DateTime.now(DateTimeZone.UTC)
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", originalFilename)
    metadata.setCacheControl("no-cache") // FIXME -- set this to something sensible

    val avatar = Avatar(
      id = avatarId,
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",
      userId = user.id,
      originalFilename = originalFilename,
      status = Pending,
      createdAt = now,
      lastModified = now,
      isSocial = true,
      isActive = false)

    for {
      avatar <- kvs.put(dynamoTable, avatar)
      _ <- fs.put(privateBucket, s"avatars/$avatarId", file, metadata)
    } yield CreatedAvatar(avatar)
  }

  def copyToPublic(avatar: Avatar): Error \/ Avatar = {
    fs.copy(
      privateBucket,
      s"avatars/${avatar.id}",
      publicBucket,
      s"user/${avatar.userId.toString}"
    ) map (_ => avatar)
  }

  def deleteFromPublic(avatar: Avatar): Error \/ Avatar = {
    fs.delete(
      publicBucket,
      s"user/${avatar.userId.toString}"
    ) map (_ => avatar)
  }

  def updateS3(old: Avatar, updated: Avatar): Error \/ Avatar = updated.status match {
    case Approved => copyToPublic(updated)
    case Rejected if old.isActive => deleteFromPublic(updated)
    case _ => updated.right
  }

  def updateStatus(id: String, status: Status): Error \/ UpdatedAvatar = {
    val oldAvatar = kvs.get(dynamoTable, id)

    if (oldAvatar.exists(_.status == status)) {
      oldAvatar map UpdatedAvatar
    } else for {
      old <- oldAvatar
      updated <- kvs.update(dynamoTable, id, status)
      _ <- updateS3(old, updated)
    } yield UpdatedAvatar(updated)
  }
}

object AvatarStore {
  def apply(): AvatarStore = AvatarStore(S3(), Dynamo())
}