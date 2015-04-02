package com.gu.adapters.store

import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, PutItemRequest}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.adapters.http.Filters
import com.gu.entities.{Approved, Avatar, Error, Inactive, Status, User}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.scalatra.servlet.FileItem

import scalaz.Scalaz._
import scalaz.\/


sealed trait Store {
  def get(filters: Filters): \/[Error, List[Avatar]]
  def get(id: String): \/[Error, Avatar]
  def get(user: User): \/[Error, List[Avatar]]
  def getActive(user: User): \/[Error, Avatar]

  def save(user: Int, file: FileItem): \/[Error, Avatar]
  def updateStatus(id: String, status: Status): \/[Error, Avatar]
}

object AvatarTestStore extends Store {
  val avatars = List(
    Avatar("123-id", "http://avatar-url-1", "", 123, "foo.gif", Approved, new DateTime(), new DateTime()),
    Avatar("abc-id", "http://avatar-url-2", "", 234, "bar.gif", Approved, new DateTime(), new DateTime())
  )

  def get(filters: Filters): \/[Error, List[Avatar]] = avatars.right
  def get(id: String): \/[Error, Avatar] = avatars.head.right
  def get(user: User): \/[Error, List[Avatar]] = avatars.right
  def getActive(user: User): \/[Error, Avatar] = avatars.head.right

  def save(user: Int, file: FileItem): \/[Error, Avatar] = ???
  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???
}

object AvatarAwsStore extends Store {

  val s3 = new AmazonS3Client(new DefaultAWSCredentialsProviderChain())
  s3.setRegion(Region.getRegion(Regions.EU_WEST_1))

  val dynamoDB = new AmazonDynamoDBClient(new DefaultAWSCredentialsProviderChain())
  dynamoDB.setRegion(Region.getRegion(Regions.EU_WEST_1))

  val conf = ConfigFactory.load()
  val apiBaseUrl = conf.getString("api.baseUrl")
  val publicBucket = conf.getString("aws.s3.public")
  val privateBucket = conf.getString("aws.s3.private")
  val dynamoDBTableName = conf.getString("aws.dynamodb.table")

  def get(filters: Filters): \/[Error, List[Avatar]] = ???
  def get(id: String): \/[Error, Avatar] = ???
  def get(user: User): \/[Error, List[Avatar]] = ???
  def getActive(user: User): \/[Error, Avatar] = ???

  def save(user: Int, file: FileItem): \/[Error, Avatar] = {

    // debug
    println(user)
    println(file.getName)
    println(file.getContentType)

    val avatarId = UUID.randomUUID.toString
    val now = new DateTime()

    // copy to S3
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", file.getName)
    metadata.setContentLength(file.getSize)
    metadata.setContentType(file.getContentType.get)
    metadata.setCacheControl("no-cache")  // FIXME -- set this to something sensible

    s3.putObject(new PutObjectRequest(
        privateBucket,
        s"avatars/$avatarId",
        file.getInputStream,
        metadata
      )
    )

    import scala.collection.JavaConverters._
//    val item = new Item()
//      .withPrimaryKey("UserId", user.toString)
//      .withString("AvatarId", avatarId)
//      .withString("Status", "inactive")
//      .withString("OriginalFilename", file.getName)
//      .withString("CreatedAt", createdAt.toString)
//      .withString("LastModified", createdAt.toString)
//      .withBoolean("IsSocial", false)
//      .withBoolean("RequiresModeration", false)

//    val item = Map[String, AttributeValue](
//      "UserId" -> new AttributeValue().withS(user.toString),
//      "AvatarId" -> new AttributeValue().withS(avatarId)
//    ).asJava

    val item = Map[String, AttributeValue](
      "ImageId" -> AttributeValues.S(avatarId),
      "UserId" -> AttributeValues.N(user),
      "OriginalFilename" -> AttributeValues.S(file.getName),
      "Status" -> AttributeValues.S("inactive"),
      "CreatedAt" -> AttributeValues.S(now.toString),
      "LastModified" -> AttributeValues.S(now.toString),
      "IsSocial" -> AttributeValues.BOOL(false),
      "RequiresModeration" -> AttributeValues.BOOL(false)
    )

    dynamoDB.putItem(new PutItemRequest()
      .withTableName(dynamoDBTableName)
      .withItem(item.asJava)
    )

    // return Avatar
    val avatar = Avatar(
      id = avatarId,
      href = s"$apiBaseUrl/avatars/$avatarId",
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",  // TODO -- is /images the right place for them?
      userId = user,
      originalFilename = file.getName,
      status = Inactive,
      createdAt = now,
      lastModified = now
    )
    avatar.right
  }

  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???

}