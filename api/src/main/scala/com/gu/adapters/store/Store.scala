package com.gu.adapters.store

import java.io.ByteArrayInputStream
import java.net.URL

import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import com.gu.core.models.Errors._
import com.gu.core.models.{Ascending, Avatar, Descending, Error, Errors, IOFailed, OrderBy, Status}
import com.gu.core.store._
import com.gu.core.utils.ErrorHandling._
import com.gu.core.utils.{ISODateFormatter, KVLocationFromID}
import org.joda.time.{DateTime, DateTimeZone}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import com.gu.auth.AWSCredentials
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import scala.concurrent.duration._
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.sns.model.InvalidStateException
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValueUpdate
import software.amazon.awssdk.services.dynamodb.model.AttributeAction
import software.amazon.awssdk.services.dynamodb.model.ReturnValue
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest
import software.amazon.awssdk.services.dynamodb.model.WriteRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse

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

case class Dynamo(client: DynamoDbClient, fs: FileStore, props: DynamoProperties) extends KVStore {

  def asAvatar(item: Map[String, AttributeValue]): Either[Error, Avatar] = {
    for {
      avatarId <- item.get("AvatarId").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: AvatarId")))
      userId <- item.get("UserId").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: UserId")))
      originalFilename <- item.get("OriginalFilename").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: OriginalFilename")))
      status <- item.get("Status").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: Status")))
      createdAt <- item.get("CreatedAt").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: CreatedAt")))
      lastModified <- item.get("LastModified").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: LastModified")))
      isSocial <- item.get("IsSocial").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: IsSocial")))
      isActive <- item.get("IsActive").toRight(Errors.dynamoRequestFailed(List("Dynamo response missing: IsActive")))

      location = KVLocationFromID(avatarId.s())
      secureUrl <- fs.presignedUrl(props.processedBucket, location)
      secureRawUrl <- fs.presignedUrl(props.rawBucket, location)
    } yield {
      Avatar(
        id = avatarId.s(),
        avatarUrl = secureUrl.toString,
        userId = userId.n(),
        originalFilename = originalFilename.s(),
        rawUrl = secureRawUrl.toString,
        status = Status(status.s()),
        createdAt = ISODateFormatter.parse(createdAt.s()),
        lastModified = ISODateFormatter.parse(lastModified.s()),
        isSocial = isSocial.bool(),
        isActive = isActive.bool()
      )
    }
  }

  def get(table: String, id: String): Either[Error, Avatar] = {
    val request = GetItemRequest.builder()
      .tableName(table)
      .key(Map("AvatarId" -> AttributeValue.builder().s(id).build()).asJava)
      .build()

    handleIoErrors(client.getItem(request))
      .filterOrElse(_ != null, avatarNotFound(List(s"avatar with ID: $id not found"))) // getItem can return null alas
      .map(item => asAvatar(item.item().asScala.toMap).toOption.get)
  }

  def query[A](
    table: String,
    index: String,
    key: String,
    value: AttributeValue,
    since: Option[DateTime] = None,
    until: Option[DateTime] = None,
    order: Option[OrderBy] = Some(Descending)
  ): Either[Error, QueryResponse] = {
    val oldestFirst = order.contains(Ascending)
    val hasBefore = until.isDefined

    val querySpec = (hasBefore, oldestFirst) match {
      case (true, true) =>   (until.map(t => (s"AND LastModified < :until", Map(":until" -> AttributeValue.builder().s(ISODateFormatter.print(t)).build()))), false)
      case (false, true) =>  (since.map(t => (s"AND LastModified > :since", Map(":since" -> AttributeValue.builder().s(ISODateFormatter.print(t)).build()))), true)
      case (true, false) =>  (until.map(t => (s"AND LastModified > :until", Map(":until" -> AttributeValue.builder().s(ISODateFormatter.print(t)).build()))), true)
      case (false, false) => (since.map(t => (s"AND LastModified < :since", Map(":since" -> AttributeValue.builder().s(ISODateFormatter.print(t)).build()))), false)
    }

    val keyConditionExpressionAndValue = querySpec._1.getOrElse(
      (
        "",
        Map.empty[String, AttributeValue]
      )
    )

    val query = QueryRequest.builder()
      .tableName(table)
      .indexName(index)
      .keyConditionExpression(s"#key = :value ${keyConditionExpressionAndValue._1}")
      .expressionAttributeNames(Map(
        "#key" -> key
      ).asJava)
      .expressionAttributeValues(
        (Map(":value" -> value) ++ keyConditionExpressionAndValue._2).asJava
      )
      .scanIndexForward(querySpec._2)
      .limit(props.pageSize)

    val result = handleIoErrors(client.query(query.build()))

    for {
      qr <- result.map(_.lastEvaluatedKey())
      items <- result.map(_.items().asScala)
      avatars = items.map(item => asAvatar(item.asScala.toMap)).flatMap(_.toOption)
    } yield {
      val orderedAvatars = until.map(_ => avatars.reverse).getOrElse(avatars).toList
      QueryResponse(orderedAvatars, true)
    }
  }

  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): Either[Error, QueryResponse] = {
    query(table, index, "UserId", AttributeValue.fromN(userId), since, until)
  }

  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): Either[Error, QueryResponse] = {
    query(table, index, "Status", AttributeValue.fromS(status.asString), since, until, order)
  }

  def put(table: String, avatar: Avatar): Either[Error, Avatar] = {
    val request = PutItemRequest.builder()
      .tableName(table)
      .item(Map(
        "AvatarId" -> AttributeValue.builder().s(avatar.id).build,
        "UserId" -> AttributeValue.builder().n(avatar.userId.toInt.toString).build,
        "OriginalFilename" -> AttributeValue.builder().s(avatar.originalFilename).build,
        "Status" -> AttributeValue.builder().s(avatar.status.asString).build,
        "CreatedAt" -> AttributeValue.builder().s(ISODateFormatter.print(avatar.createdAt)).build,
        "LastModified" -> AttributeValue.builder().s(ISODateFormatter.print(avatar.lastModified)).build,
        "IsSocial" -> AttributeValue.builder().bool(avatar.isSocial).build,
        "IsActive" -> AttributeValue.builder().bool(avatar.isActive).build
      ).asJava)
      .build()

    handleIoErrors(client.putItem(request)) map (_ => avatar)
  }

  def update(table: String, id: String, status: Status, isActive: Boolean = false): Either[Error, Avatar] = {
    val now = DateTime.now(DateTimeZone.UTC)
    val request = UpdateItemRequest.builder()
      .tableName(table)
      .key(Map("AvatarId" -> AttributeValue.builder().s(id).build).asJava)
      .attributeUpdates(Map(
        "Status" -> AttributeValueUpdate.builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.builder().s(status.asString).build())
          .build(),
        "IsActive" -> AttributeValueUpdate.builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.builder().bool(isActive).build())
          .build(),
        "LastModified" -> AttributeValueUpdate.builder()
          .action(AttributeAction.PUT)
          .value(AttributeValue.builder().s(ISODateFormatter.print(now)).build())
          .build()
      ).asJava)
      .returnValues(ReturnValue.ALL_NEW)
      .build()
    for {
      item <- handleIoErrors(client.updateItem(request)).map(_.attributes().asScala.toMap)
      avatar <- asAvatar(item)
    } yield avatar
  }

  def delete(table: String, ids: List[String]): Either[Error, DeleteResponse] = {
    def check(r: BatchWriteItemResponse): Either[Error, BatchWriteItemResponse] = {
      val errors: List[String] = r
        .unprocessedItems()
        .asScala
        .get(table)
        .toSeq
        .flatMap(_.iterator().asScala)
        .map(_.deleteRequest().key().get("AvatarId").s())
        .toList

      if (errors.nonEmpty) {
        Left(deletionFailed(errors))
      } else {
        Right(r)
      }
    }
    def delete() = {
      val deleteRequests = ids.map(id => WriteRequest.builder().deleteRequest(
        DeleteRequest.builder()
          .key(Map("AvatarId" -> AttributeValue.builder().s(id).build()).asJava)
          .build()
      ).build()).asJava;

      val request = BatchWriteItemRequest.builder()
        .requestItems(Map(table -> deleteRequests).asJava)
        .build()

      for {
        response <- handleIoErrors(client.batchWriteItem(request))
        _ <- check(response)
      } yield DeleteResponse(ids)
    }

    ids match {
      case Nil => Right(DeleteResponse(ids))
      case _ => delete()
    }
  }
}

object Dynamo {
  def apply(awsRegion: Region, props: DynamoProperties): Dynamo = {
    val client = DynamoDbClient.builder()
      .credentialsProvider(AWSCredentials.awsCredentials)
      .region(awsRegion).build()
    Dynamo(client, S3(awsRegion), props)
  }
}

case class S3(client: S3Client, presignerClient: S3Presigner) extends FileStore {

  def getMetadata(bucket: String, key: String): Either[Error, Map[String, String]] = {
    val request = HeadObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()

    Right(client.headObject(request).metadata().asScala.toMap)
  }

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): Either[Error, Unit] = {

    val request = CopyObjectRequest.builder()
      .sourceBucket(fromBucket)
      .sourceKey(fromKey)
      .destinationBucket(toBucket)
      .destinationKey(toKey)
      .build()

    handleIoErrors(client.copyObject(request))
  }

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: PutObjectRequest.Builder
  ): Either[Error, Unit] = {
    val request = metadata
      .bucket(bucket)
      .key(key)
      .contentLength(file.length)
      .build()

    handleIoErrors(client.putObject(request, RequestBody.fromBytes(file)))
  }

  def delete(bucket: String, keys: String*): Either[Error, Unit] = {
    if (keys.nonEmpty) {
      val toDelete = Delete.builder()
        .objects(keys.map(ObjectIdentifier.builder().key(_).build()).asJava)
        .build()
      val request = DeleteObjectsRequest.builder()
        .bucket(bucket).delete(toDelete).build()
      val resp = Try(client.deleteObjects(request))

      // We need to unpack MultiObjectDeleteExceptions to ensure errors
      // returned/logged are useful
      val withErrors = resp match {
        case Success(_) => Right(())
        case Failure(err) => Left(ioError(err))
      }

      withErrors.left.map(e => logError("Multi-delete failed", e))
      withErrors
    } else {
      Right(())
    }
  }

  def presignedUrl(
    bucket: String,
    key: String
  ): Either[Error, URL] = {
    val request = GetObjectPresignRequest.builder()
      .getObjectRequest(
        GetObjectRequest.builder()
          .bucket(bucket)
          .key(key)
          .build()
      )
      .signatureDuration(java.time.Duration.ofMinutes(20))
      .build()

    handleIoErrors(presignerClient.presignGetObject(request).url())
  }
}

object S3 {
  def apply(awsRegion: Region): S3 = {
    val client = S3Client.builder()
      .credentialsProvider(AWSCredentials.awsCredentials)
      .region(awsRegion)
      .build()

    val presignerClient = S3Presigner.builder()
      .credentialsProvider(AWSCredentials.awsCredentials)
      .region(awsRegion)
      .build()

    S3(client, presignerClient)
  }
}
