package com.gu.adapters.store

import java.io.ByteArrayInputStream
import java.net.URL

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.Region
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document._
import com.amazonaws.services.dynamodbv2.document.spec.{QuerySpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.core.models.Errors._
import com.gu.core.models.{Avatar, Error, Status}
import com.gu.core.store._
import com.gu.core.utils.ErrorHandling._
import com.gu.core.utils.{ISODateFormatter, KVLocationFromID}
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/}

object AWSCredentials {
  val awsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("gu-aws-discussion"),
    new DefaultAWSCredentialsProviderChain()
  )
}

case class DynamoProperties(
  pageSize: Int,
  rawBucket: String,
  processedBucket: String
)

object DynamoProperties {
  def apply(storeProps: StoreProperties): DynamoProperties = DynamoProperties(
    pageSize = storeProps.pageSize,
    rawBucket = storeProps.fsRawBucket,
    processedBucket = storeProps.fsProcessedBucket
  )
}

case class Dynamo(db: DynamoDB, fs: FileStore, props: DynamoProperties) extends KVStore {

  def asAvatar(item: Item): Error \/ Avatar = {
    val avatarId = item.getString("AvatarId")
    val location = KVLocationFromID(avatarId)

    for {
      secureUrl <- fs.presignedUrl(props.processedBucket, location)
      secureRawUrl <- fs.presignedUrl(props.rawBucket, location)
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
    handleIoErrors(db.getTable(table).getItem("AvatarId", id))
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
      .withMaxResultSize(props.pageSize)

    if (until.isEmpty) spec.withScanIndexForward(false)
    since.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").lt(ISODateFormatter.print(t))))
    until.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").gt(ISODateFormatter.print(t))))

    val result = handleIoErrors(db.getTable(table).getIndex(index).query(spec))

    for {
      pages <- result.map(_.pages.asScala.toList)
      qr <- result.map(_.getLastLowLevelResult.getQueryResult)
      items = pages.flatMap(_.asScala)
      avatars = items.map(item => asAvatar(item)).flatMap(_.toOption)
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

    handleIoErrors(db.getTable(table).putItem(item)) map (_ => avatar)
  }

  def update(table: String, id: String, status: Status, isActive: Boolean = false): Error \/ Avatar = {
    val now = DateTime.now(DateTimeZone.UTC)
    val spec = new UpdateItemSpec()
      .withPrimaryKey("AvatarId", id)
      .withAttributeUpdate(
        new AttributeUpdate("Status").put(status.asString),
        new AttributeUpdate("IsActive").put(isActive),
        new AttributeUpdate("LastModified").put(ISODateFormatter.print(now))
      )
      .withReturnValues(ReturnValue.ALL_NEW)
    for {
      item <- handleIoErrors(db.getTable(table).updateItem(spec)).map(_.getItem)
      avatar <- asAvatar(item)
    } yield avatar
  }
}

object Dynamo {
  def apply(awsRegion: Region, props: DynamoProperties): Dynamo = {
    val client = new AmazonDynamoDBClient(AWSCredentials.awsCredentials)
    client.setRegion(awsRegion)
    Dynamo(new DynamoDB(client), S3(awsRegion), props)
  }
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
    handleIoErrors(client.copyObject(request))
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
    handleIoErrors(client.putObject(request))
  }

  def delete(bucket: String, key: String): Error \/ Unit = {
    val request = new DeleteObjectRequest(bucket, key)
    handleIoErrors(client.deleteObject(request))
  }

  def presignedUrl(
    bucket: String,
    key: String,
    expiration: DateTime = DateTime.now(DateTimeZone.UTC).plusMinutes(20)
  ): Error \/ URL = {
    val request = new GeneratePresignedUrlRequest(bucket, key)
    request.setExpiration(expiration.toDate)
    handleIoErrors(client.generatePresignedUrl(request))
  }
}

object S3 {
  def apply(awsRegion: Region): S3 = {
    val client = new AmazonS3Client(AWSCredentials.awsCredentials)
    client.setRegion(awsRegion)
    S3(client)
  }
}
