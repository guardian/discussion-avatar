package com.gu.core.store

import com.gu.core.models.{Approved, Avatar, Error, Rejected, Status, UpdatedAvatar}
import com.gu.core.utils.ErrorHandling.logIfError

import scalaz.Scalaz._
import scalaz.\/

class AvatarUpdateService(avatarStore: AvatarStore) {
   private def handleApproved(oldAvatarE: \/[Error, Avatar]) = {
    for {
      oldAvatar <- oldAvatarE
      _ <- avatarStore.deleteActiveAvatar(oldAvatar.userId)
      updated <- avatarStore.updateKv(oldAvatar.id, Approved, isActive = true)
      _ <- avatarStore.copyToPublic(updated)
    } yield updated
  }

  private def handleRejected(oldAvatarE: \/[Error, Avatar]) = {
    for {
      oldAvatar <- oldAvatarE
      updated <- avatarStore.updateKv(oldAvatar.id, Rejected, isActive = false)
      _ <- if (oldAvatar.isActive) avatarStore.deletePublicAvatarFile(updated.userId).map(_ => ()) else ().right
    } yield updated
  }

  def updateStatus(id: String, status: Status): Error \/ UpdatedAvatar = {
    val oldAvatar: \/[Error, Avatar] = avatarStore.get(id).map(_.body)

    val result = status match {
      case noChange if oldAvatar.exists(_.status == status) => oldAvatar
      case Approved => handleApproved(oldAvatar)
      case Rejected => handleRejected(oldAvatar)
      case otherStatus => avatarStore.updateKv(id, otherStatus, isActive = false)
    }

    logIfError(s"Unable to update status for Avatar ID: $id. Avatar may be left in an inconsistent state.", result.map(UpdatedAvatar.apply))
  }
}
