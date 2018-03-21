package com.gu.core.store

import java.net.URL
import java.util.UUID

import com.amazonaws.regions.Region
import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.core.models.Errors._
import com.gu.core.models._
import com.gu.core.utils.ErrorHandling.logIfError
import com.gu.core.utils.{EscapedUnicode, KVLocationFromID}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, DateTimeZone}

import scalaz.Scalaz._
import scalaz.{-\/, NonEmptyList, \/, \/-}

case class QueryResponse(
  avatars: List[Avatar],
  hasMore: Boolean
)

case class DeleteResponse(
  ids: List[String]
)

trait KVStore {
  def get(table: String, id: String): Error \/ Avatar
  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): Error \/ QueryResponse
  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): Error \/ QueryResponse
  def put(table: String, avatar: Avatar): Error \/ Avatar
  def update(table: String, id: String, status: Status, isActive: Boolean = false): Error \/ Avatar
  def delete(table: String, ids: List[String]): Error \/ DeleteResponse
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

  def delete(bucket: String, keys: String*): Error \/ Unit

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
      qr <- kvs.query(dynamoTable, statusIndex, filters.status, filters.since, filters.until, filters.order)
    } yield FoundAvatars(qr.avatars, qr.hasMore)
  }

  def get(avatarId: String): Error \/ FoundAvatar = {
    kvs.get(dynamoTable, avatarId) map FoundAvatar
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

  def updateStatus(id: String, status: Status): Error \/ UpdatedAvatar = {
    def demoteActive(user: User): Error \/ Unit = {
      def recoverNotFound(e: Error): Error \/ Unit = e match {
        case AvatarNotFound(_, _) => ().right
        case err => err.left
      }

      getActive(user)
        .flatMap(active => updateKv(active.body.id, active.body.status, isActive = false))
        .fold(recoverNotFound, _ => ().right)
    }

    def handleApproved(user: User, avatar: Avatar) = {
      for {
        _ <- demoteActive(user)
        updated <- updateKv(avatar.id, Approved, isActive = true)
        _ <- copyToPublic(updated)
        _ <- cleanupInactive(user)
      } yield updated
    }

    def handleRejected(user: User, avatar: Avatar) = {
      for {
        updated <- updateKv(avatar.id, Rejected, isActive = false)
        _ <- if (avatar.isActive) deletePublicAvatarFile(updated.userId).map(_ => ()) else ().right
      } yield updated
    }

    val result = for {
      avatar <- get(id).map(_.body)
      user = User(avatar.userId)
      updated <- status match {
        case noChange if avatar.status == status => avatar.right
        case Approved => handleApproved(user, avatar)
        case Rejected => handleRejected(user, avatar)
        case otherStatus => updateKv(id, otherStatus, isActive = false)
      }
    } yield updated

    logIfError(s"Unable to update status for Avatar ID: $id. Avatar may be left in an inconsistent state.", result.map(UpdatedAvatar.apply))
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
    val status = if (isSocial) Inactive else Pending

    val created = for {
      secureUrl <- fs.presignedUrl(processedBucket, location)
      secureRawUrl <- fs.presignedUrl(rawBucket, location)
      avatar <- kvs.put(
        dynamoTable,
        Avatar(
          id = avatarId.toString,
          avatarUrl = secureUrl.toString,
          userId = user.id.toString,
          originalFilename = originalFilename,
          rawUrl = secureRawUrl.toString,
          status = status,
          createdAt = now,
          lastModified = now,
          isSocial = isSocial,
          isActive = false
        )
      )
      _ <- fs.put(incomingBucket, location, file, objectMetadata(avatarId, user, originalFilename, mimeType))
    } yield CreatedAvatar(avatar)

    logIfError(s"Unable to create Avatar with ID: $id. Avatar may be left in an inconsistent state.", created)
  }

  def copyToPublic(avatar: Avatar): Error \/ Avatar = {
    val location = KVLocationFromID(avatar.id)
    fs.copy(
      processedBucket,
      location,
      publicBucket,
      s"user/${avatar.userId}"
    ) map (_ => avatar)
  }

  def cleanupInactive(user: User): Error \/ UserCleaned = {
    val avatars = for {
      resp <- get(user)
    } yield resp.body

    val actives = avatars.map(_.filter(_.isActive)).getOrElse(Nil)

    if (actives.size > 1) {
      logger.error(s"User ${user.id} has multiple (${actives.size} active avatars")
    }

    if (actives.nonEmpty) {
      for {
        as <- avatars
        inactiveIDs = as.filterNot(_.isActive).map(_.id)
        buckets = List(incomingBucket, rawBucket, processedBucket)

        _ <- deleteAvatarKvEntries(inactiveIDs)
        locations <- deletePrivateAvatarFiles(buckets, inactiveIDs)
      } yield UserCleaned(user, resources(inactiveIDs, locations))
    } else {
      UserCleaned(user, Nil).right // no-op
    }
  }

  private[this] def resources(ids: List[String], locations: List[String]): List[String] = {
    ids.map(id => s"kv:$id") ::: locations.map(l => s"fs:$l")
  }

  def deleteAll(user: User): Error \/ UserDeleted = {
    for {
      avatars <- get(user)
      ids = avatars.body.map(_.id)
      buckets = List(incomingBucket, rawBucket, processedBucket)

      _ <- deletePublicAvatarFile(user.id)
      locations <- deletePrivateAvatarFiles(buckets, ids)
      _ <- deleteAvatarKvEntries(ids)
    } yield UserDeleted(user, resources(ids, locations))
  }

  private[this] def objectMetadata(avatarId: UUID, user: User, originalFilename: String, mimeType: String): ObjectMetadata = {
    val metadata = new ObjectMetadata()
    metadata.addUserMetadata("avatar-id", avatarId.toString)
    metadata.addUserMetadata("user-id", user.toString)
    metadata.addUserMetadata("original-filename", EscapedUnicode(originalFilename))
    metadata.setCacheControl("max-age=3600")
    metadata.setContentType(mimeType)
    metadata
  }

  private[this] def deletePublicAvatarFile(userId: String): \/[Error, String] = {
    val location = s"user/$userId"
    fs.delete(publicBucket, s"user/$userId").map(_ => location)
  }

  private[this] def deletePrivateAvatarFiles(buckets: Seq[String], avatarIds: List[String]): \/[Error, List[String]] = {
    val paths = avatarIds.map(KVLocationFromID.apply)

    val zero = List[String]().right[Error]
    buckets.foldLeft(zero){ (acc, bucket) =>
      acc match {
        case \/-(locAcc) => {
          val locations = paths.map(path => s"$bucket/$path")
          val res = fs.delete(bucket, paths: _*).map(_ => locations)
          res.map(_ ::: locAcc)
        }
        case error => error
      }
    }
  }

  private[this] def deleteAvatarKvEntries(avatarIds: List[String]): \/[Error, DeleteResponse] = kvs.delete(dynamoTable, avatarIds)

  private[this] def deleteActiveAvatar(userId: String): \/[Error, DeleteResult] = {
    def delete(avatar: Avatar) = {
      val buckets = List(incomingBucket, rawBucket, processedBucket)
      for {
        locations <- deletePrivateAvatarFiles(buckets, avatar.id :: Nil)
        publicImage <- deletePublicAvatarFile(avatar.userId)
        _ <- deleteAvatarKvEntries(avatar.id :: Nil)
      } yield {
        AvatarDeleted(avatar, publicImage :: locations)
      }
    }

    getActive(User(userId)).map(_.body) match {
      case \/-(avatar) => delete(avatar)
      case -\/(AvatarNotFound(_, _)) => AvatarNotDeleted(userId).right
      case e: -\/[Error] => e
    }
  }

  private[this] def updateKv(avatarId: String, status: Status, isActive: Boolean): \/[Error, Avatar] = {
    kvs.update(dynamoTable, avatarId, status, isActive = isActive)
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

