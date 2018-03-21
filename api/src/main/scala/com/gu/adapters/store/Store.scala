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
import com.gu.core.models.{Ascending, Avatar, Descending, Error, Errors, IOFailed, OrderBy, Status}
import com.gu.core.store._
import com.gu.core.utils.ErrorHandling._
import com.gu.core.utils.{ISODateFormatter, KVLocationFromID}
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._
import scalaz.{-\/, NonEmptyList, \/, \/-}

object AWSCredentials {
  val awsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("discussion"),
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
        userId = item.getString("UserId"),
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
    until: Option[DateTime] = None,
    order: Option[OrderBy] = Some(Descending)
  ): Error \/ QueryResponse = {

    val spec = new QuerySpec()
      .withHashKey(key, value)
      .withMaxResultSize(props.pageSize)

    val hasBefore = until.isDefined
    val oldestFirst = order.contains(Ascending)

    if (hasBefore && oldestFirst) {
      until.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").lt(ISODateFormatter.print(t))))
      spec.withScanIndexForward(false)
    } else if (!hasBefore && oldestFirst) {
      since.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").gt(ISODateFormatter.print(t))))
      spec.withScanIndexForward(true)
    } else if (hasBefore) {
      until.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").gt(ISODateFormatter.print(t))))
      spec.withScanIndexForward(true)
    } else if (!hasBefore) {
      since.foreach(t => spec.withRangeKeyCondition(new RangeKeyCondition("LastModified").lt(ISODateFormatter.print(t))))
      spec.withScanIndexForward(false)
    }

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

  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse = {
    query(table, index, "UserId", userId.toInt, since, until)
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): Error \/ QueryResponse = {
    query(table, index, "Status", status.asString, since, until, order)
  }

  def put(table: String, avatar: Avatar): Error \/ Avatar = {
    val item = new Item()
      .withPrimaryKey("AvatarId", avatar.id)
      .withNumber("UserId", avatar.userId.toInt)
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

  def delete(table: String, ids: List[String]): Error \/ DeleteResponse = {
    def check(r: BatchWriteItemResult): Error \/ BatchWriteItemResult = {
      val errors: List[String] = r
        .getUnprocessedItems
        .asScala
        .get(table)
        .toSeq
        .flatMap(_.iterator().asScala)
        .map(_.getDeleteRequest.getKey.get("AvatarId").getS)
        .toList

      if (errors.nonEmpty) {
        deletionFailed(errors.toNel.getOrElse(NonEmptyList("Unable to list IDs"))).left
      } else {
        r.right
      }
    }
    def delete() = {
      val deleteRequest = new TableWriteItems(table).addHashOnlyPrimaryKeysToDelete("AvatarId", ids: _*)
      for {
        response <- handleIoErrors(db.batchWriteItem(deleteRequest)).map(_.getBatchWriteItemResult)
        _ <- check(response)
      } yield DeleteResponse(ids)
    }

    ids match {
      case Nil => DeleteResponse(ids).right
      case _ => delete()
    }
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

  def delete(bucket: String, keys: String*): Error \/ Unit = {
    if (keys.nonEmpty) {
      val request = new DeleteObjectsRequest(bucket).withKeys(keys: _*)
      val resp = Try(client.deleteObjects(request))

      // We need to unpack MultiObjectDeleteExceptions to ensure errors
      // returned/logged are useful
      val withErrors = resp match {
        case Success(_) => ().right
        case Failure(ex: MultiObjectDeleteException) =>
          val errors = ex.getErrors.asScala.toList.map(ex => s"Unable to delete object ${ex.getKey}, reason: ${ex.getMessage}").mkString(", ")
          Errors.ioFailed(errors.wrapNel).left
        case Failure(err) => ioError(err).left
      }

      withErrors.leftMap(e => logError("Multi-delete failed", e))
      withErrors
    } else {
      ().right
    }
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
