package com.gu.adapters.store

import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.adapters.config.Config
import com.gu.core.models._
import com.gu.core.store.AvatarStore
import com.gu.core.utils.KVLocationFromID
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.mock.MockitoSugar

import scalaz.\/-

class AvatarStoreTest extends FlatSpec with Matchers with MockitoSugar {

  trait WithStore {
    val config = Config()
    val storeProps = config.storeProperties
    val fileStore = new TestFileStore(storeProps.fsProcessedBucket)
    val kvStore = new TestKVStore(storeProps.kvTable)
    val avatarStore = AvatarStore(fileStore, kvStore, storeProps)
    val userId = "123456"

    def uploadAvatar(data: String): Avatar = {
      val avatar = avatarStore.userUpload(User(userId), data.getBytes, "image/png", "test.png").getOrElse(throw new RuntimeException).body
      fileStore.put(storeProps.fsRawBucket, KVLocationFromID(avatar.id), data.getBytes, new ObjectMetadata())
      fileStore.put(storeProps.fsProcessedBucket, KVLocationFromID(avatar.id), data.getBytes, new ObjectMetadata())
      avatar
    }
  }

  "after uploading an avatar it" should "become active and public on approve" in new WithStore {
    val avatar = uploadAvatar("orig")
    avatarStore.updateStatus(avatar.id, Approved) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = true, status = Approved)))
    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe true
    fileStore.get(storeProps.fsPublicBucket, s"user/$userId").get shouldBe "orig"
  }

  "after uploading a second avatar with approval, it" should "delete the original active avatar" in new WithStore {
    val originalAvatar = uploadAvatar("orig")
    avatarStore.updateStatus(originalAvatar.id, Approved)

    val avatar = uploadAvatar("new")
    avatarStore.updateStatus(avatar.id, Approved) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = true, status = Approved)))

    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe true
    fileStore.get(storeProps.fsPublicBucket, s"user/$userId").get shouldBe "new"

    fileStore.exists(storeProps.fsProcessedBucket, KVLocationFromID(originalAvatar.id)) shouldBe false
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(originalAvatar.id)) shouldBe false

    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
  }

  "rejecting an avatar" should "delete the public avatar and set active to false but not delete the file" in new WithStore {
    val avatar = uploadAvatar("orig")
    avatarStore.updateStatus(avatar.id, Approved)
    avatarStore.updateStatus(avatar.id, Rejected) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = false, status = Rejected)))

    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    fileStore.exists(storeProps.fsRawBucket, KVLocationFromID(avatar.id)) shouldBe true
    kvStore.getKey(storeProps.kvTable, avatar.id).get.isActive shouldBe false
    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe false
  }

  "rejecting an avatar" should "not delete a different public avatar" in new WithStore {
    val original = uploadAvatar("orig")
    avatarStore.updateStatus(original.id, Approved)

    val avatar = uploadAvatar("new")
    avatarStore.updateStatus(avatar.id, Rejected) shouldBe \/-(UpdatedAvatar(avatar.copy(isActive = false, status = Rejected)))

    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe true
  }

  "receiving Inactive on an active avatar" should "make the avatar inactive but not delete the public image" in new WithStore {
    val original = uploadAvatar("orig")
    avatarStore.updateStatus(original.id, Approved)
    avatarStore.updateStatus(original.id, Pending) shouldBe \/-(UpdatedAvatar(original.copy(isActive = false, status = Pending)))
    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe true
    kvStore.getKey(storeProps.kvTable, original.id).get.isActive shouldBe false
  }

  "deleting a user" should "remove key value (Dynamo) data and also associated files (S3)" in new WithStore {
    val avatar1 = uploadAvatar("first")
    val avatar2 = uploadAvatar("second")
    avatarStore.updateStatus(avatar1.id, Approved)

    val user = User(userId)

    // check files and records exist
    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe true

    val privateBuckets = Set(storeProps.fsRawBucket, storeProps.fsIncomingBucket, storeProps.fsProcessedBucket)

    for {
      bucket <- privateBuckets
      avatar <- Set(avatar1, avatar2)
    } {
      withClue(s"Avatar should exist in bucket $bucket") {
        fileStore.exists(bucket, KVLocationFromID(avatar.id)) shouldBe true
      }
    }

    // now delete and verify gone
    avatarStore.deleteAll(user)

    avatarStore.get(user).getOrElse(FoundAvatars(List(avatar1), false)).body shouldBe Nil

    fileStore.exists(storeProps.fsPublicBucket, s"user/$userId") shouldBe false

    privateBuckets.foreach { bucket =>
      withClue(s"Avatar should no longer exist in bucket $bucket") {
        fileStore.exists(bucket, KVLocationFromID(avatar1.id)) shouldBe false
      }
    }
  }
}
