package com.gu.adapters.store

import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.adapters.config.Config
import com.gu.core.models._
import com.gu.core.store.{AvatarStore, AvatarUpdateService}
import com.gu.core.utils.KVLocationFromID
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.mock.MockitoSugar

import scalaz.\/-

class AvatarUpdateServiceTest extends FlatSpec with Matchers with MockitoSugar {

  trait AvatarUpdateServiceScope {
    val config = Config()
    val storeProps = config.storeProperties
    val fileStore = new TestFileStore(storeProps.fsProcessedBucket)
    val kvStore = new TestKVStore(storeProps.kvTable)
    val avatarStore = AvatarStore(fileStore, kvStore, storeProps)
    val avatarUpdateService = new AvatarUpdateService(avatarStore)
    val userId = "123456"

    def uploadAvatar(data: String): Avatar = {
      val avatar = avatarStore.userUpload(User(userId), data.getBytes, "image/png", "test.png").getOrElse(throw new RuntimeException).body
      fileStore.put(storeProps.fsRawBucket, KVLocationFromID(avatar.id), data.getBytes, new ObjectMetadata())
      fileStore.put(storeProps.fsProcessedBucket, KVLocationFromID(avatar.id), data.getBytes, new ObjectMetadata())
      avatar
    }
  }

  "after uploading an avatar it" should "become active and public on approve" in new AvatarUpdateServiceScope {
    val avatar = uploadAvatar("orig")
    avatarUpdateService.updateStatus(avatar.id, Approved) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = true, status = Approved)))
    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe true
    fileStore.get(storeProps.fsPublicBucket, s"user/$userId").get shouldBe "orig"
  }

  "after uploading a second avatar with approval, it" should "delete the original active avatar" in new AvatarUpdateServiceScope {
    val originalAvatar = uploadAvatar("orig")
    avatarUpdateService.updateStatus(originalAvatar.id, Approved)

    val avatar = uploadAvatar("new")
    avatarUpdateService.updateStatus(avatar.id, Approved) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = true, status = Approved)))

    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe true
    fileStore.get(storeProps.fsPublicBucket, s"user/$userId").get shouldBe "new"

    fileStore.exists(storeProps.fsProcessedBucket, KVLocationFromID(originalAvatar.id)) shouldBe false
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(originalAvatar.id)) shouldBe false

    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
  }

  "rejecting an avatar" should "delete the public avatar and set active to false but not delete the file" in new AvatarUpdateServiceScope {
    val avatar = uploadAvatar("orig")
    avatarUpdateService.updateStatus(avatar.id, Approved)
    avatarUpdateService.updateStatus(avatar.id, Rejected) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = false, status = Rejected)))

    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe false
    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe false
  }

  "rejecting an avatar" should "not delete a different public avatar" in new AvatarUpdateServiceScope {
    val original = uploadAvatar("orig")
    avatarUpdateService.updateStatus(original.id, Approved)

    val avatar = uploadAvatar("new")
    avatarUpdateService.updateStatus(avatar.id, Rejected) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = false, status = Rejected)))

    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe true
  }

  "receiving Inactive on an active avatar" should "make the avatar inactive but not delete the public image" in new AvatarUpdateServiceScope {
    val original = uploadAvatar("orig")
    avatarUpdateService.updateStatus(original.id, Approved)
    avatarUpdateService.updateStatus(original.id, Pending) shouldBe \/-(UpdatedAvatar(original.copy(isActive = false, status = Pending)))
    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe true
    kvStore.getKey(storeProps.kvTable, original.id).get.isActive shouldBe false
  }

}
