package com.gu.adapters.store

import java.io.{ ByteArrayInputStream }
import java.net.URL
import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{ Region, Regions }
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{ QuerySpec, UpdateItemSpec }
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.adapters.http.Filters
import com.gu.adapters.utils.Attempt._
import com.gu.adapters.utils.{ MakeS3Folder, ISODateFormatter }
import com.gu.core.Errors._
import com.gu.core.{ Config, _ }
import org.joda.time.{ DateTime, DateTimeZone }

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz.{ NonEmptyList, \/ }

case class QueryResponse(
  avatars: List[Avatar],
  hasMore: Boolean
)

trait KVStore {
  def get(table: String, id: String): Error \/ Avatar
  def query(table: String, index: String, userId: Int, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse
  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse
  def put(table: String, avatar: Avatar): Error \/ Avatar
  def update(table: String, id: String, status: Status): Error \/ Avatar
}

case class Dynamo(db: DynamoDB, fs: FileStore) extends KVStore {

  val pageSize = Config.pageSize
  val rawBucket = Config.s3RawBucket
  val processedBucket = Config.s3ProcessedBucket

  def asAvatar(item: Item): Error \/ Avatar = {
    val avatarId = item.getString("AvatarId")
    val folder = MakeS3Folder(avatarId)

    for {
      secureUrl <- fs.presignedUrl(processedBucket, s"$folder/$avatarId")
      secureRawUrl <- fs.presignedUrl(rawBucket, s"$folder/$avatarId")
    } yield {
      Avatar(
        id = avatarId,
        avatarUrl = secureUrl.toString,
        userId = item.getString("UserId").toInt,
        originalFilename = item.getString("OriginalFilename"),
        rawUrl = secureRawUrl.toString,
        status = Status(item.getString("Status")),
        createdAt = ISODateFormatter.parse(item.getString("CreatedAt")),
        lastModified = ISODateFormatter.parse(item.getString("LastModified")),
        isSocial = item.getString("IsSocial").toBoolean,
        isActive = item.getString("IsActive").toBoolean
      )
    }
  }

  def get(table: String, id: String): Error \/ Avatar = {
    io(db.getTable(table).getItem("AvatarId", id))
      .ensure(avatarNotFound(NonEmptyList(s"avatar with ID: $id not found")))(_ != null) // getItem can return null alas
      .map(item => asAvatar(item).toOption.get)
  }

  def query[A](
    table: String,
    index: String,
    key: String,
    value: A,
    since: Option[DateTime] = None,
    until: Option[DateTime] = None
  ): Error \/ QueryResponse = {

    val spec = new QuerySpec()
      .withHashKey(key, value)
      .withMaxResultSize(pageSize)

    if (!until.isDefined) spec.withScanIndexForward(false)
    since.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").lt(ISODateFormatter.print(t))))
    until.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").gt(ISODateFormatter.print(t))))

    val result = io(db.getTable(table).getIndex(index).query(spec))

    for {
      pages <- result.map(_.pages.asScala.toList)
      qr <- result.map(_.getLastLowLevelResult.getQueryResult)
      items = pages.map(_.asScala).flatten
      avatars = items.map(item => asAvatar(item)).map(_.toOption).flatten
    } yield {
      val orderedAvatars = until.map(_ => avatars.reverse).getOrElse(avatars)
      QueryResponse(orderedAvatars, qr.getLastEvaluatedKey != null)
    }
  }

  def query(table: String, index: String, userId: Int, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse = {
    query(table, index, "UserId", userId, since, until)
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse = {
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
    val now = DateTime.now(DateTimeZone.UTC)
    val spec = new UpdateItemSpec()
      .withPrimaryKey("AvatarId", id)
      .withAttributeUpdate(
        new AttributeUpdate("Status").put(status.asString),
        new AttributeUpdate("LastModified").put(ISODateFormatter.print(now))
      )
      .withReturnValues(ReturnValue.ALL_NEW)
    for {
      item <- io(db.getTable(table).updateItem(spec)).map(_.getItem)
      avatar <- asAvatar(item)
    } yield avatar
  }
}

object Dynamo {
  def apply(): Dynamo = {
    val client = new AmazonDynamoDBClient(new DefaultAWSCredentialsProviderChain())
    client.setRegion(Region.getRegion(Regions.EU_WEST_1))
    Dynamo(new DynamoDB(client), S3())
  }
}

trait FileStore {
  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): Error \/ Unit

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: ObjectMetadata
  ): Error \/ Unit

  def delete(bucket: String, key: String): Error \/ Unit

  def presignedUrl(
    bucket: String,
    key: String,
    expiration: DateTime = DateTime.now(DateTimeZone.UTC).plusMinutes(20)
  ): Error \/ URL
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
    toKey: String
  ): Error \/ Unit = {

    val request = new CopyObjectRequest(fromBucket, fromKey, toBucket, toKey)
    io(client.copyObject(request))
  }

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: ObjectMetadata
  ): Error \/ Unit = {

    val inputStream = new ByteArrayInputStream(file)
    metadata.setContentLength(file.length)
    val request = new PutObjectRequest(bucket, key, inputStream, metadata)
    io(client.putObject(request))
  }

  def delete(bucket: String, key: String): Error \/ Unit = {
    val request = new DeleteObjectRequest(bucket, key)
    io(client.deleteObject(request))
  }

  def presignedUrl(
    bucket: String,
    key: String,
    expiration: DateTime = DateTime.now(DateTimeZone.UTC).plusMinutes(20) // expires in 15 minutes
  ): Error \/ URL = {
    val request = new GeneratePresignedUrlRequest(bucket, key)
    request.setExpiration(expiration.toDate)
    io(client.generatePresignedUrl(request))
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

  val incomingBucket = Config.s3IncomingBucket
  val rawBucket = Config.s3RawBucket
  val processedBucket = Config.s3ProcessedBucket
  val publicBucket = Config.s3PublicBucket
  val dynamoTable = Config.dynamoTable
  val statusIndex = Config.statusIndex
  val userIndex = Config.userIndex

  def get(filters: Filters): \/[Error, FoundAvatars] = {
    for {
      qr <- kvs.query(dynamoTable, statusIndex, filters.status, filters.since, filters.until)
    } yield FoundAvatars(qr.avatars, qr.hasMore)
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
        None
      )
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

  def objectMetadata(avatarId: UUID, user: User, originalFilename: String, mimeType: String): ObjectMetadata = {
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId.toString)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", originalFilename)
    metadata.setCacheControl("no-cache") // FIXME -- set this to something sensible
    metadata.setContentType(mimeType)
    metadata
  }

  def userUpload(
    user: User,
    file: Array[Byte],
    mimeType: String,
    originalFilename: String,
    isSocial: Boolean = false
  ): Error \/ CreatedAvatar = {

    val avatarId = UUID.randomUUID
    val now = DateTime.now(DateTimeZone.UTC)
    val folder = MakeS3Folder(avatarId.toString)

    for {
      secureUrl <- fs.presignedUrl(incomingBucket, s"$folder/$avatarId")
      secureRawUrl <- fs.presignedUrl(rawBucket, s"$folder/$avatarId")
      avatar <- kvs.put(
        dynamoTable,
        Avatar(
          id = avatarId.toString,
          avatarUrl = secureUrl.toString,
          userId = user.id,
          originalFilename = originalFilename,
          rawUrl = secureRawUrl.toString,
          status = Pending,
          createdAt = now,
          lastModified = now,
          isSocial = true,
          isActive = false
        )
      )
      _ <- fs.put(incomingBucket, s"$folder/$avatarId", file, objectMetadata(avatarId, user, originalFilename, mimeType))
    } yield CreatedAvatar(avatar)
  }

  def migratedUserUpload(
    user: User,
    originalFile: Array[Byte],
    originalFileMimeType: String,
    processedFile: Array[Byte],
    processedFileMimeType: String,
    originalFilename: String,
    createdAt: DateTime,
    isSocial: Boolean
  ): Error \/ CreatedAvatar = {

    def migrate: Error \/ Avatar = {

      val avatarId = UUID.randomUUID
      val now = DateTime.now(DateTimeZone.UTC)
      val folder = MakeS3Folder(avatarId.toString)

      for {
        secureUrl <- fs.presignedUrl(processedBucket, s"$folder/$avatarId")
        secureRawUrl <- fs.presignedUrl(rawBucket, s"$folder/$avatarId")
        avatar <- kvs.put(
          dynamoTable,
          Avatar(
            id = avatarId.toString,
            avatarUrl = secureUrl.toString,
            userId = user.id,
            originalFilename = originalFilename,
            rawUrl = secureRawUrl.toString,
            status = Approved,
            createdAt = createdAt,
            lastModified = now,
            isSocial = isSocial,
            isActive = true
          )
        )
        _ <- fs.put(rawBucket, s"$folder/$avatarId", originalFile, objectMetadata(avatarId, user, originalFilename, originalFileMimeType))
        _ <- fs.put(processedBucket, s"$folder/$avatarId", processedFile, objectMetadata(avatarId, user, originalFilename, processedFileMimeType))
        _ <- copyToPublic(avatar)
      } yield avatar
    }

    for {
      _ <- getActive(user).swap
        .leftMap(_ => avatarAlreadyExists(NonEmptyList(s"User ${user.id} already has active avatar")))
      avatar <- migrate
    } yield CreatedAvatar(avatar)
  }

  def copyToPublic(avatar: Avatar): Error \/ Avatar = {
    val folder = MakeS3Folder(avatar.id)
    fs.copy(
      processedBucket,
      s"$folder/${avatar.id}",
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