package com.gu.adapters.store

import java.io.InputStream
import java.util.UUID

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import com.gu.adapters.http.Filters
import com.gu.entities.{Avatar, Error, Status, User, Inactive, Pending, Approved, Rejected}
import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime

import scalaz.Scalaz._
import scalaz.\/


sealed trait Store {
  def get(filters: Filters): \/[Error, List[Avatar]]
  def get(id: String): \/[Error, Avatar]
  def get(user: User): \/[Error, List[Avatar]]
  def getActive(user: User): \/[Error, Avatar]

  def save(user: Int, filename: String, file: InputStream, contentType: String): \/[Error, Avatar]
  def updateStatus(id: String, status: Status): \/[Error, Avatar]
}

object AvatarTestStore extends Store {
  val avatars = List(
    Avatar("123-id", "http://avatar-url-1", 123, "foo.gif", Approved, new DateTime()),
    Avatar("abc-id", "http://avatar-url-2", 234, "bar.gif", Approved, new DateTime())
  )

  def get(filters: Filters): \/[Error, List[Avatar]] = avatars.right
  def get(id: String): \/[Error, Avatar] = avatars.head.right
  def get(user: User): \/[Error, List[Avatar]] = avatars.right
  def getActive(user: User): \/[Error, Avatar] = avatars.head.right

  def save(user: Int, filename: String, file: InputStream, contentType: String): \/[Error, Avatar] = ???
  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???
}

object AvatarAwsStore extends Store {

  val s3 = new AmazonS3Client(new DefaultAWSCredentialsProviderChain())
  s3.setRegion(Region.getRegion(Regions.EU_WEST_1))

  val conf = ConfigFactory.load()
  val publicBucket = conf.getString("aws.s3.public")
  val privateBucket = conf.getString("aws.s3.private")

  def get(filters: Filters): \/[Error, List[Avatar]] = ???
  def get(id: String): \/[Error, Avatar] = ???
  def get(user: User): \/[Error, List[Avatar]] = ???
  def getActive(user: User): \/[Error, Avatar] = ???

  def save(user: Int, filename: String, file: InputStream, contentType: String): \/[Error, Avatar] = {

    // debug
    println(user)
    println(filename)
    println(contentType)

    val avatarId = UUID.randomUUID.toString
    val createdAt = new DateTime()

    // copy to S3
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", filename)
    metadata.setContentType(contentType)
    metadata.setCacheControl("no-cache")  // FIXME -- set this to something sensible

    s3.putObject(new PutObjectRequest(
        privateBucket,
        s"images/$avatarId",
        file,
        metadata
      )
    )

    // update DynamoDB


    // return Avatar
    val avatar = Avatar(
      avatarId,
      s"/images/$avatarId",
      user,
      filename,
      Inactive,
      createdAt
    )
    avatar.right
  }

  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???

}