package com.gu.core.store

import java.net.URL
import java.util.UUID

import com.amazonaws.regions.Region
import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.core.models.Errors._
import com.gu.core.models._
import com.gu.core.utils.ErrorHandling.logIfError
import com.gu.core.utils.{ ASCII, KVLocationFromID }
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{ DateTime, DateTimeZone }

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
  def update(table: String, id: String, status: Status, isActive: Boolean = false): Error \/ Avatar
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

case class AvatarStore(fs: FileStore, kvs: KVStore, props: StoreProperties) extends LazyLogging {

  val incomingBucket = props.fsIncomingBucket
  val rawBucket = props.fsRawBucket
  val processedBucket = props.fsProcessedBucket
  val publicBucket = props.fsPublicBucket
  val dynamoTable = props.kvTable
  val statusIndex = props.kvStatusIndex
  val userIndex = props.kvUserIndex

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
    metadata.addUserMetadata("original-filename", ASCII(originalFilename))
    metadata.setCacheControl("max-age=3600")
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
    val location = KVLocationFromID(avatarId.toString)

    for {
      secureUrl <- fs.presignedUrl(processedBucket, location)
      secureRawUrl <- fs.presignedUrl(rawBucket, location)
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
          isSocial = isSocial,
          isActive = false
        )
      )
      _ <- fs.put(incomingBucket, s"$location/$avatarId", file, objectMetadata(avatarId, user, originalFilename, mimeType))
    } yield CreatedAvatar(avatar)
  }

  def copyToPublic(avatar: Avatar): Error \/ Avatar = {
    val location = KVLocationFromID(avatar.id)
    fs.copy(
      processedBucket,
      location,
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

  def updateFileStore(old: Avatar, updated: Avatar): Error \/ Avatar = updated.status match {
    case Approved => copyToPublic(updated)
    case Rejected if old.isActive => deleteFromPublic(updated)
    case _ => updated.right
  }

  def updateStatus(id: String, status: Status): Error \/ UpdatedAvatar = {
    val oldAvatar = kvs.get(dynamoTable, id)

    val result = status match {
      case noChange if oldAvatar.exists(_.status == status) => oldAvatar map UpdatedAvatar

      case Approved =>
        for {
          old <- oldAvatar
          active = getActive(User(old.userId))
            .map(a => kvs.update(dynamoTable, a.body.id, a.body.status, isActive = false))
          updated <- kvs.update(dynamoTable, id, status, isActive = true)
          _ <- updateFileStore(old, updated)
        } yield UpdatedAvatar(updated)

      case _ =>
        for {
          old <- oldAvatar
          updated <- kvs.update(dynamoTable, id, status, isActive = false)
          _ <- updateFileStore(old, updated)
        } yield UpdatedAvatar(updated)
    }

    logIfError(s"Unable to update status for Avatar ID: $id. Avatar may be left in an inconsistent state.", result)
  }
}

case class StoreProperties(
  awsRegion: Region,
  fsIncomingBucket: String,
  fsRawBucket: String,
  fsProcessedBucket: String,
  fsPublicBucket: String,
  kvTable: String,
  kvStatusIndex: String,
  kvUserIndex: String,
  pageSize: Int
)

