package com.gu.core.store

import java.net.URL
import java.util.UUID

import com.gu.core.models.Errors._
import com.gu.core.models._
import com.gu.core.utils.ErrorHandling.logIfError
import com.gu.core.utils.{EscapedUnicode, KVLocationFromID}
import com.typesafe.scalalogging.LazyLogging
import org.joda.time.{DateTime, DateTimeZone}

import scala.util.Try
import scala.jdk.CollectionConverters._
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.regions.Region

case class QueryResponse(
  avatars: List[Avatar],
  hasMore: Boolean
)

case class DeleteResponse(
  ids: List[String]
)

trait KVStore {
  def get(table: String, id: String): Either[Error, Avatar]
  def query(table: String, index: String, userId: String, since: Option[DateTime], until: Option[DateTime]): Either[Error, QueryResponse]
  def query(table: String, index: String, status: Status, since: Option[DateTime], until: Option[DateTime], order: Option[OrderBy]): Either[Error, QueryResponse]
  def put(table: String, avatar: Avatar): Either[Error, Avatar]
  def update(table: String, id: String, status: Status, isActive: Boolean = false): Either[Error, Avatar]
  def delete(table: String, ids: List[String]): Either[Error, DeleteResponse]
}

trait FileStore {
  def copy(
    fromBucket: String,
    fromKey: String,
    toBucket: String,
    toKey: String
  ): Either[Error, Unit]

  def put(
    bucket: String,
    key: String,
    file: Array[Byte],
    metadata: PutObjectRequest.Builder
  ): Either[Error, Unit]

  def delete(bucket: String, keys: String*): Either[Error, Unit]

  def presignedUrl(
    bucket: String,
    key: String
  ): Either[Error, URL]
}

case class AvatarStore(fs: FileStore, kvs: KVStore, props: StoreProperties) extends LazyLogging {

  val incomingBucket = props.fsIncomingBucket
  val rawBucket = props.fsRawBucket
  val processedBucket = props.fsProcessedBucket
  val publicBucket = props.fsPublicBucket
  val dynamoTable = props.kvTable
  val statusIndex = props.kvStatusIndex
  val userIndex = props.kvUserIndex

  def get(filters: Filters): Either[Error, FoundAvatars] = {
    for {
      qr <- kvs.query(dynamoTable, statusIndex, filters.status, filters.since, filters.until, filters.order)
    } yield FoundAvatars(qr.avatars, qr.hasMore)
  }

  def get(avatarId: String): Either[Error, FoundAvatar] = {
    kvs.get(dynamoTable, avatarId) map FoundAvatar
  }

  def get(user: User): Either[Error, FoundAvatars] = {
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

  // Beware - everything about this function assumes small n
  def getAll(user: User): Either[Error, FoundAvatars] = {
    def loop(acc: List[Avatar], since: Option[DateTime]): Either[Error, FoundAvatars] = {
      val resp = kvs.query(
        dynamoTable,
        userIndex,
        user.id,
        since,
        None
      )

      resp match {
        case Right(found) if found.hasMore => loop(acc ::: found.avatars, found.avatars.lastOption.map(_.lastModified))
        case Right(last) => Right(FoundAvatars(body = acc ::: last.avatars, hasMore = false))
        case Left(error) => Left(error)
      }
    }

    loop(Nil, None)
  }

  def getActive(user: User): Either[Error, FoundAvatar] = {
    for {
      found <- get(user)
      avatar <- found.body.find(_.isActive).toRight(
        avatarNotFound(List(s"No active avatar found for user: ${user.id}."))
      )
    } yield FoundAvatar(avatar)
  }

  def getPersonal(user: User): Either[Error, FoundAvatar] = {
    for {
      found <- get(user)
      avatar <- found.body.find(a => a.isActive || a.status == Inactive)
        .toRight(avatarNotFound(List(s"No active avatar found for user: ${user.id}.")))
    } yield FoundAvatar(avatar)
  }

  def updateStatus(id: String, status: Status): Either[Error, UpdatedAvatar] = {
    def demoteActive(user: User): Either[Error, Unit] = {
      def recoverNotFound(e: Error): Either[Error, Unit] = e match {
        case AvatarNotFound(_, _) => Right(())
        case err => Left(err)
      }

      getActive(user)
        .flatMap(active => updateKv(active.body.id, active.body.status, isActive = false))
        .fold(recoverNotFound, _ => Right(()))
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
        _ <- Right(if (avatar.isActive) deletePublicAvatarFile(updated.userId).map(_ => ()) else ())
      } yield updated
    }

    val result = for {
      avatar <- get(id).map(_.body)
      user = User(avatar.userId)
      updated <- status match {
        case noChange if avatar.status == status => Right(avatar)
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
  ): Either[Error, CreatedAvatar] = {

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

    logIfError(s"Unable to create Avatar with ID: $avatarId. Avatar may be left in an inconsistent state.", created)
  }

  def copyToPublic(avatar: Avatar): Either[Error, Avatar] = {
    val location = KVLocationFromID(avatar.id)
    fs.copy(
      processedBucket,
      location,
      publicBucket,
      s"user/${avatar.userId}"
    ) map (_ => avatar)
  }

  def cleanupInactive(user: User): Either[Error, UserCleaned] = {
    val avatars = for {
      resp <- getAll(user)
    } yield resp.body

    val actives = avatars.map(_.filter(_.isActive)).getOrElse(Nil)
    val inactives = avatars.map(_.filterNot(_.isActive)).getOrElse(Nil)

    if (actives.size > 1) {
      logger.error(s"User ${user.id} has multiple (${actives.size} active avatars")
    }

    if (actives.nonEmpty && inactives.nonEmpty) {
      val buckets = List(incomingBucket, rawBucket, processedBucket)
      val inactiveIDs = inactives.map(_.id)

      for {
        resources <- deleteAvatars(buckets, inactiveIDs)
      } yield UserCleaned(user, resources)
    } else {
      Right(UserCleaned(user, Nil)) // no-op
    }
  }

  private[this] def resources(ids: List[String], locations: List[String]): List[String] = {
    ids.map(id => s"kv:$id") ::: locations.map(l => s"fs:$l")
  }

  def deleteAll(user: User): Either[Error, UserDeleted] = {
    for {
      avatars <- get(user)
      ids = avatars.body.map(_.id)
      buckets = List(incomingBucket, rawBucket, processedBucket)
      _ <- deletePublicAvatarFile(user.id)
      resources <- deleteAvatars(buckets, ids)
    } yield UserDeleted(user, resources)
  }

  private[this] def objectMetadata(avatarId: UUID, user: User, originalFilename: String, mimeType: String): PutObjectRequest.Builder = {
    PutObjectRequest.builder()
      .metadata(Map(
        "avatar-id" -> avatarId.toString,
        "user-id" -> user.id.toString,
        "original-filename" -> EscapedUnicode(originalFilename)
      ).asJava)
      .cacheControl("max-age=3600")
      .contentType(mimeType)
      .bucket(incomingBucket)
      .key(KVLocationFromID(avatarId.toString))
  }

  private[this] def deletePublicAvatarFile(userId: String): Either[Error, String] = {
    val location = s"user/$userId"
    fs.delete(publicBucket, s"user/$userId").map(_ => location)
  }

  private[this] def deletePrivateAvatarFiles(buckets: Seq[String], avatarIds: List[String]): Either[Error, List[String]] = {
    val paths = avatarIds.map(KVLocationFromID.apply)

    val zero: Either[Error, List[String]] = Right(List())
    buckets.foldLeft(zero) { (acc, bucket) =>
      acc match {
        case Right(locAcc) => {
          val locations = paths.map(path => s"$bucket/$path")
          val res = fs.delete(bucket, paths: _*).map(_ => locations)
          res.map(_ ::: locAcc)
        }
        case error => error
      }
    }
  }

  private[this] def deleteAvatars(buckets: Seq[String], ids: List[String]): Either[Error, List[String]] = {
    def delete(ids: List[String]): Either[Error, List[String]] = {
      for {
        locations <- deletePrivateAvatarFiles(buckets, ids)
        _ <- deleteAvatarKvEntries(ids)
      } yield resources(ids, locations)
    }

    val awsBatchLimit = 25
    val groups = ids.sliding(awsBatchLimit)

    // batch as AWS apis can only handle up to 25 items at a time
    val zero: Either[Error, List[String]] = Right(List.empty)
    val rs = groups.foldLeft(zero) {
      case (acc, ids) =>
        acc.flatMap(locations => delete(ids).map(_ ::: locations))
    }

    rs
  }

  private[this] def deleteAvatarKvEntries(avatarIds: List[String]): Either[Error, DeleteResponse] = kvs.delete(dynamoTable, avatarIds)

  private[this] def deleteActiveAvatar(userId: String): Either[Error, DeleteResult] = {
    def delete(avatar: Avatar) = {
      val buckets = List(incomingBucket, rawBucket, processedBucket)
      for {
        locations <- deleteAvatars(buckets, List(avatar.id))
        publicImage <- deletePublicAvatarFile(avatar.userId)
      } yield {
        AvatarDeleted(avatar, publicImage :: locations)
      }
    }

    getActive(User(userId)).map(_.body) match {
      case Right(avatar) => delete(avatar)
      case Left(AvatarNotFound(_, _)) => Right(AvatarNotDeleted(userId))
      case Left(e: Error) => Left(e)
    }
  }

  private[this] def updateKv(avatarId: String, status: Status, isActive: Boolean): Either[Error, Avatar] = {
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

