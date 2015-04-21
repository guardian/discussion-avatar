package com.gu.adapters.store

import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.spec.{QuerySpec, UpdateItemSpec}
import com.amazonaws.services.dynamodbv2.document.{AttributeUpdate, DynamoDB, Item}
import com.amazonaws.services.dynamodbv2.model._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.adapters.http.Filters
import com.gu.entities.Errors.{dynamoRequestFailed, avatarRetrievalFailed}
import com.gu.entities._
import com.typesafe.config.ConfigFactory
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import org.scalatra.servlet.FileItem

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}
import scalaz.Scalaz._
import scalaz.{-\/, NonEmptyList, \/, \/-}


sealed trait Store {
  def get(filters: Filters): \/[Error, List[Avatar]]
  def get(id: String): \/[Error, Avatar]
  def get(user: User): \/[Error, List[Avatar]]
  def getActive(user: User): \/[Error, String]

  def fetchImage(user: User, url: String): \/[Error, Avatar]
  def userUpload(user: User, file: FileItem): \/[Error, Avatar]
  def updateStatus(id: String, status: Status): \/[Error, Avatar]

  def getStats: \/[Error, String]
}

object AvatarTestStore extends Store {
  val avatars = List(
    Avatar("123-id", "http://api", "http://avatar-url-1", 123, "foo.gif", Approved, new DateTime(), new DateTime(), isSocial = true, isActive = true),
    Avatar("abc-id", "http://api", "http://avatar-url-2", 234, "bar.gif", Approved, new DateTime(), new DateTime(), isSocial = false, isActive = false)
  )

  def get(filters: Filters): \/[Error, List[Avatar]] = avatars.right
  def get(id: String): \/[Error, Avatar] = avatars.head.right
  def get(user: User): \/[Error, List[Avatar]] = avatars.right
  def getActive(user: User): \/[Error, String] = avatars.head.avatarUrl.right

  def fetchImage(user: User, url: String): \/[Error, Avatar] = ???
  def userUpload(user: User, file: FileItem): \/[Error, Avatar] = ???
  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???

  def getStats: \/[Error, String] = ???
}

object AvatarAwsStore extends Store {

  val conf = ConfigFactory.load()
  val apiBaseUrl = conf.getString("api.baseUrl")
  val publicBucket = conf.getString("aws.s3.public")
  val privateBucket = conf.getString("aws.s3.private")
  val dynamoDBTableName = conf.getString("aws.dynamodb.table")

  val s3 = new AmazonS3Client(new DefaultAWSCredentialsProviderChain())
  s3.setRegion(Region.getRegion(Regions.EU_WEST_1))

  val client = new AmazonDynamoDBClient(new DefaultAWSCredentialsProviderChain())
  client.setRegion(Region.getRegion(Regions.EU_WEST_1))

  val dynamoDB = new DynamoDB(client)

  val dateFormat = ISODateTimeFormat.dateTimeNoMillis

  def get(filters: Filters): \/[Error, List[Avatar]] = {

    // FIXME -- Return everything if all? or require status=pending&status=approved etc?

    val index = dynamoDB.getTable(dynamoDBTableName).getIndex("Status-AvatarId-index")

    val response = Try {

      val items = index.query(new QuerySpec()
        .withHashKey("Status", filters.status.asString)
        .withMaxPageSize(2) // FIXME -- set to something real, 2 is just to test paging
        .withMaxResultSize(100) // FIXME - set this to a realistic limit
      )

      (for {
        page <- items.pages().asScala
        item <- page.asScala
      } yield {
        val avatarId = item.getString("AvatarId")
        Avatar(
          id = avatarId,
          url = s"$apiBaseUrl/avatars/$avatarId",
          avatarUrl = s"http://$privateBucket/avatars/$avatarId",
          userId = item.getInt("UserId"),
          originalFilename = item.getString("OriginalFilename"),
          status = Status(item.getString("Status")),
          createdAt = dateFormat.parseDateTime(item.getString("CreatedAt")),
          lastModified = dateFormat.parseDateTime(item.getString("LastModified")),
          isSocial = false,
          isActive = false
        )
      }).toList
    }

    response match {
      case Success(avatars) => \/-(avatars sortWith(_.lastModified isAfter _.lastModified))
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def get(id: String): \/[Error, Avatar] = {

    val table = dynamoDB.getTable(dynamoDBTableName)

    val response = Try {

      val item = table.getItem("AvatarId", id)

      val avatarId = item.getString("AvatarId")
      Avatar(
        id = avatarId,
        url = s"$apiBaseUrl/avatars/$avatarId",
        avatarUrl = s"http://$privateBucket/avatars/$avatarId",
        userId = item.getInt("UserId"),
        originalFilename = item.getString("OriginalFilename"),
        status = Status(item.getString("Status")),
        createdAt = dateFormat.parseDateTime(item.getString("CreatedAt")),
        lastModified = dateFormat.parseDateTime(item.getString("LastModified")),
        isSocial = false,
        isActive = false
      )
    }

    response match {
      case Success(avatar) => \/-(avatar)
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def get(user: User): \/[Error, List[Avatar]] = {

    val index = dynamoDB.getTable(dynamoDBTableName).getIndex("UserId-AvatarId-index")

    val response = Try {

      val items = index.query("UserId", user.id)

      (for {
        page <- items.pages().asScala
        item <- page.asScala
      } yield {
        val avatarId = item.getString("AvatarId")
        Avatar(
          id = avatarId,
          url = s"$apiBaseUrl/avatars/$avatarId",
          avatarUrl = s"http://$privateBucket/avatars/$avatarId",
          userId = item.getInt("UserId"),
          originalFilename = item.getString("OriginalFilename"),
          status = Status(item.getString("Status")),
          createdAt = dateFormat.parseDateTime(item.getString("CreatedAt")),
          lastModified = dateFormat.parseDateTime(item.getString("LastModified")),
          isSocial = false,
          isActive = false
        )
      }).toList
    }

    response match {
      case Success(avatars) => \/-(avatars sortWith (_.lastModified isAfter _.lastModified))
      case Failure(error) => -\/(avatarRetrievalFailed(NonEmptyList(error.getMessage)))
    }
  }

  def getActive(user: User): \/[Error, String] = {

    // FIXME -- more /user/me logic goes here!!!

    s"http://$publicBucket/users/${user.id}".right

  }

  def fetchImage(user: User, url: String) = {

    import java.net.URL

    val file = new URL(url).openStream()

    val avatarId = UUID.randomUUID.toString
    val now = DateTime.now(DateTimeZone.UTC)

    // copy to S3
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", url)
    // metadata.setContentLength(file.getSize)
    // metadata.setContentType(file.getContentType.get)
    metadata.setCacheControl("no-cache")  // FIXME -- set this to something sensible

    s3.putObject(new PutObjectRequest(
        privateBucket,
        s"avatars/$avatarId",
        file,
        metadata
      )
    )
    copyToPublic(user, avatarId)

    val table = dynamoDB.getTable(dynamoDBTableName)

    // Build the item
    val item = new Item()
      .withPrimaryKey("AvatarId", avatarId)
      .withNumber("UserId", user.id)
      .withString("OriginalFilename", url)
      .withString("Status", Pending.asString)
      .withString("CreatedAt", dateFormat.print(now))
      .withString("LastModified", dateFormat.print(now))
      .withBoolean("IsSocial", false)
      .withBoolean("IsActive", false)

    println(dateFormat.print(now))

    // Write the item to the table
    table.putItem(item)

    // return Avatar
    val avatar = Avatar(
      id = avatarId,
      url = s"$apiBaseUrl/avatars/$avatarId",
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",
      userId = user.id,
      originalFilename = url,
      status = Inactive,
      createdAt = now,
      lastModified = now,
      isSocial = true,
      isActive = false
    )
    avatar.right
  }

  def userUpload(user: User, file: FileItem): \/[Error, Avatar] = {

    val avatarId = UUID.randomUUID.toString
    val now = DateTime.now(DateTimeZone.UTC)

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

    val table = dynamoDB.getTable(dynamoDBTableName)

    // Build the item
    val item = new Item()
      .withPrimaryKey("AvatarId", avatarId)
      .withNumber("UserId", user.id)
      .withString("OriginalFilename", file.getName)
      .withString("Status", Pending.asString)
      .withString("CreatedAt", dateFormat.print(now))
      .withString("LastModified", dateFormat.print(now))
      .withBoolean("IsSocial", false)
      .withBoolean("IsActive", false)

    println(dateFormat.print(now))

    // Write the item to the table
    table.putItem(item)

    // return Avatar
    val avatar = Avatar(
      id = avatarId,
      url = s"$apiBaseUrl/avatars/$avatarId",
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",
      userId = user.id,
      originalFilename = file.getName,
      status = Pending,
      createdAt = now,
      lastModified = now,
      isSocial = false,
      isActive = false
    )
    avatar.right
  }

  def copyToPublic(user: User, avatarId: String) = {
    println(s"copy to public s3://$privateBucket/avatars/$avatarId -> s3://$publicBucket/user/${user.id.toString}")
    s3.copyObject(
      privateBucket,
      s"avatars/$avatarId",
      publicBucket,
      s"user/${user.id.toString}"
    )
  }

  def deleteFromPublic(user: User) = {
    s3.deleteObject(
      publicBucket,
      s"user/${user.id.toString}"
    )
  }

  def updateStatus(id: String, status: Status): \/[Error, Avatar] = {

    val table = dynamoDB.getTable(dynamoDBTableName)

    val update = new UpdateItemSpec()
      .withPrimaryKey("AvatarId", id)
      .withAttributeUpdate(
        new AttributeUpdate("Status").put(status.asString)
      )
      .withReturnValues(ReturnValue.ALL_NEW)

    val outcome = table.updateItem(update)
    val item = outcome.getItem

    val avatarId = item.getString("AvatarId")
    val user = User(item.getInt("UserId"))

    // get uuid of this user's avatar in public and if same as this one delete from public
    val userMetadata = s3.getObjectMetadata(privateBucket, s"avatars/$avatarId").getUserMetadata
    val avatarIdOfPublic = userMetadata.get("avatar-id")

    status match {
      case Approved => copyToPublic(user, avatarId) // FIXME -- set isActive=true and false for others
      case Rejected => if (avatarIdOfPublic.equals(avatarId)) deleteFromPublic(user) // FIXME -- opp. of above
      case _ => None
    }

    Avatar(
      id = id,
      url = s"$apiBaseUrl/avatars/$avatarId",
      avatarUrl = s"http://$privateBucket/avatars/$avatarId",
      userId = user.id,
      originalFilename = item.getString("OriginalFilename"),
      status = Status(item.getString("Status")),
      createdAt = dateFormat.parseDateTime(item.getString("CreatedAt")),
      lastModified = dateFormat.parseDateTime(item.getString("LastModified")),
      isSocial = false,
      isActive = false
    ).right
  }

  def getStats = {

    val table = dynamoDB.getTable(dynamoDBTableName)

    val response = Try {
      table.getDescription.getItemCount
    }

    response match {
      case Success(count) => \/-(count.toString)
      case Failure(error) => -\/(dynamoRequestFailed(NonEmptyList(error.getMessage)))
    }

  }
}