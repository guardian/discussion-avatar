package com.gu.adapters.store

import java.io.InputStream
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
import com.gu.core.Errors.{avatarNotFound, avatarRetrievalFailed}
import com.gu.core._
import com.typesafe.config.ConfigFactory
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._
import scalaz.{-\/, NonEmptyList, \/, \/-}

object ISODateFormatter {
  val dateFormat = ISODateTimeFormat.dateTimeNoMillis
  def parse(s: String): DateTime = dateFormat.parseDateTime(s)
  def print(dt: DateTime): String = dateFormat.print(dt)
}

case class Dynamo(db: DynamoDB) {

  val conf = ConfigFactory.load()
  val apiBaseUrl = conf.getString("api.baseUrl")
  val privateBucket = conf.getString("aws.s3.private")

  def asAvatar(item: Item, baseUrl: String, avatarUrl: String): Avatar = {
    Avatar(
      id = item.getString("AvatarId"),
      url = s"$baseUrl/avatars/$id",
      avatarUrl = s"http://$avatarUrl/avatars/$id",
      userId = item.getString("userId").toInt,
      originalFilename = item.getString("OriginalFilename"),
      status = Status(item.getString("Status")),
      createdAt = ISODateFormatter.parse(item.getString("CreatedAt")),
      lastModified = ISODateFormatter.parse(item.getString("LastModified")),
      isSocial = item.getString("isSocial").toBoolean,
      isActive = item.getString("isActive").toBoolean
    )
  }

  def get(table: String, id: String): Avatar = {
    val item = db.getTable(table).getItem("AvatarId", id)
    asAvatar(item, apiBaseUrl, privateBucket)
  }

  def query(table: String, index: String, key: String, value: String): List[Avatar] = {
    val spec = new QuerySpec()
      .withHashKey(key, value)
      .withMaxPageSize(10)
      .withMaxResultSize(100)
    val items = db.getTable(table).getIndex(index).query(spec)

    for {
      page <- items.pages.asScala.toList
      item <- page.asScala
    } yield asAvatar(item, apiBaseUrl, privateBucket)
  }

  def put(table: String, avatar: Avatar): PutItemOutcome = {
    val item = new Item()
      .withPrimaryKey("AvatarId", avatar.id)
      .withNumber("UserId", avatar.userId)
      .withString("OriginalFilename", avatar.originalFilename)
      .withString("Status", avatar.status.asString)
      .withString("CreatedAt", ISODateFormatter.print(avatar.createdAt))
      .withString("LastModified", ISODateFormatter.print(avatar.lastModified))
      .withBoolean("IsSocial", avatar.isSocial)
      .withBoolean("IsActive", avatar.isActive)

    db.getTable(table).putItem(item)
  }

  def update(table: String, id: String, status: Status): Avatar = {
    val spec = new UpdateItemSpec()
      .withPrimaryKey("AvatarId", id)
      .withAttributeUpdate(new AttributeUpdate("Status").put(status.asString))
      .withReturnValues(ReturnValue.ALL_NEW)
    val result = db.getTable(table).updateItem(spec)
    asAvatar(result.getItem, apiBaseUrl, privateBucket)
  }
}

object Dynamo {
  def apply(): Dynamo = {
    val client = new AmazonDynamoDBClient(new DefaultAWSCredentialsProviderChain())
      .withRegion(Region.getRegion(Regions.EU_WEST_1))
    Dynamo(new DynamoDB(client))
  }
}

case class S3(client: AmazonS3Client) {

  def getMetadata(bucket: String, key: String): \/[Error, ObjectMetadata] = {
    val request = new GetObjectMetadataRequest(bucket, key)
    client.getObjectMetadata(request).right
  }

  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String): \/[Error, CopyObjectResult] = {

    val request = new CopyObjectRequest(fromBucket, "fromKey", toBucket, "toKey")
    client.copyObject(request).right
  }

  def put(
    bucket: String,
    key: String,
    file: InputStream,
    metadata: ObjectMetadata): \/[Error, PutObjectResult] = {

    val request = new PutObjectRequest(bucket, key, file, metadata)
    client.putObject(request).right
  }

  def delete(bucket: String, key: String): \/[Error, Unit] = {
    val request = new DeleteObjectRequest(bucket, key)
    client.deleteObject(request).right
  }
}

object S3 {
  def apply(): S3 = {
    val client =
      new AmazonS3Client(new DefaultAWSCredentialsProviderChain())
        .withRegion(Region.getRegion(Regions.EU_WEST_1))
    S3(client)
  }
}


sealed trait Store {
  def get(filters: Filters): \/[Error, List[Avatar]]
  def get(id: String): \/[Error, Avatar]
  def get(user: User): \/[Error, List[Avatar]]
  def getActive(user: User): \/[Error, Avatar]

  def fetchImage(user: User, url: String): \/[Error, Avatar]
  def userUpload(user: User, file: InputStream, originalFilename: String, isSocial: Boolean = false): \/[Error, Avatar]
  def updateStatus(id: String, status: Status): \/[Error, Avatar]
}

object AvatarTestStore extends Store {
  val avatars = List(
    Avatar(
      "123",
      "http://api",
      "http://avatar-url-1",
      123,
      "foo.gif",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = true,
      isActive = true),
    Avatar(
      "abc",
      "http://api",
      "http://avatar-url-2",
      234,
      "bar.gif",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false)
  )

  def find(p: Avatar => Boolean): \/[Error, Avatar] = avatars.find(p) match {
    case Some(avatar) => avatar.right
    case None => -\/(avatarNotFound(NonEmptyList("Avatar not found in test store!")))
  }

  def filter(p: Avatar => Boolean): \/[Error, List[Avatar]] = avatars.filter(p) match {
    case Nil => -\/(avatarNotFound(NonEmptyList("No matching avatars in test store!")))
    case avatars => avatars.right
  }

  def get(filters: Filters): \/[Error, List[Avatar]] = {
    avatars.filter(_.status == filters.status).right
  }

  def get(id: String): \/[Error, Avatar] = find(_.id == id)

  def get(user: User): \/[Error, List[Avatar]] = {
    filter(_.userId == user.id)
  }

  def getActive(user: User): \/[Error, Avatar] = {
    find(avatar => avatar.userId == user.id && avatar.isActive)
  }

  def fetchImage(user: User, url: String): \/[Error, Avatar] = ???

  def userUpload(
    user: User, file: InputStream,
    originalFilename: String,
    isSocial: Boolean = false): \/[Error, Avatar] = ???

  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???

  def getStats: \/[Error, String] = ???
}

case class AvatarAwsStore(s3: S3, dynamoDB: Dynamo) extends Store {

  val conf = ConfigFactory.load()
  val apiBaseUrl = conf.getString("api.baseUrl")
  val publicBucket = conf.getString("aws.s3.public")
  val privateBucket = conf.getString("aws.s3.private")
  val dynamoTable = conf.getString("aws.dynamodb.table")
  val statusIndex = "Status-AvatarId-index"
  val userIndex = "UserId-AvatarId-index"
  
  def get(filters: Filters): \/[Error, List[Avatar]] = {
    val result = Try {
      dynamoDB.query(
        dynamoTable,
        statusIndex,
        "Status",
        filters.status.asString)
    }

    result match {
      case Success(avatars) => \/-(avatars sortWith(_.lastModified isAfter _.lastModified))
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def get(id: String): \/[Error, Avatar] = {
    val response = Try(dynamoDB.get(dynamoTable, id))
    response match {
      case Success(avatar) => \/-(avatar)
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def get(user: User): \/[Error, List[Avatar]] = {
    val response = Try {
      dynamoDB.query(
        dynamoTable,
        userIndex,
        "UserId",
        user.id.toString)
    }

    response match {
      case Success(avatars) => \/-(avatars sortWith (_.lastModified isAfter _.lastModified))
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def getActive(user: User): \/[Error, Avatar] = {

    // FIXME -- more /user/me logic goes here!!!

    s"http://$publicBucket/users/${user.id}".right
    ???
  }

  def fetchImage(user: User, url: String) = {
    val file = new java.net.URL(url).openStream()
    userUpload(user, file, url, true)
  }

  def userUpload(user: User, file: InputStream, originalFilename: String, isSocial: Boolean = false): \/[Error, Avatar] = {
    val avatarId = UUID.randomUUID.toString
    val now = DateTime.now(DateTimeZone.UTC)

    // copy to S3
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", originalFilename)
    metadata.setCacheControl("no-cache")  // FIXME -- set this to something sensible

    s3.put(
      privateBucket,
      s"avatars/$avatarId",
      file,
      metadata)

    copyToPublic(s3, user, avatarId)

    val avatar = Avatar(
      id = avatarId,
      url = s"$apiBaseUrl/avatars/$avatarId",
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",
      userId = user.id,
      originalFilename = originalFilename,
      status = Pending,
      createdAt = now,
      lastModified = now,
      isSocial = true,
      isActive = false)

    dynamoDB.put(dynamoTable, avatar)

    avatar.right
  }

  def copyToPublic(s3: S3, user: User, avatarId: String) = {
    println(s"copy to public s3://$privateBucket/avatars/$avatarId -> s3://$publicBucket/user/${user.id.toString}")
    s3.copy(
      privateBucket,
      s"avatars/$avatarId",
      publicBucket,
      s"user/${user.id.toString}"
    )
  }

  def deleteFromPublic(user: User) = {
    s3.delete(
      publicBucket,
      s"user/${user.id.toString}"
    )
  }

  def updateStatus(id: String, status: Status): \/[Error, Avatar] = {
    val oldAvatar = dynamoDB.get(dynamoTable, id)
    val oldStatus = Status(oldAvatar.status.asString)
    val user = User(oldAvatar.userId)

    if (status == oldStatus) {
      oldAvatar.right
    } else {
      val updatedAvatar = dynamoDB.update(dynamoTable, id, status)

      status match {
        case Approved => copyToPublic(s3, user, id)
        case Rejected if oldAvatar.isActive => deleteFromPublic(user)
        case _ =>
      }

      updatedAvatar.right
    }
  }
}

object AvatarAwsStore {
  def apply(): AvatarAwsStore = AvatarAwsStore(S3(), Dynamo())
}