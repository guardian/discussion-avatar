package com.gu.adapters.store

import com.amazonaws.services.s3.model.ObjectMetadata
import com.gu.adapters.config.Config
import com.gu.core.models._
import com.gu.core.store.AvatarStore
import com.gu.core.utils.KVLocationFromID
import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
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
    val avatar = uploadAvatar("first")
    val user = User(userId)

    avatarStore.updateStatus(avatar.id, Approved)

    val bucketPath1 = KVLocationFromID(avatar.id)
    val bucketPath2 = KVLocationFromID(avatar.id)

    val deleted = avatarStore.deleteAll(user)
    val expected = List(
      s"kv:${avatar.id}",
      s"fs:com-gu-avatar-processed-dev/$bucketPath1",
      s"fs:com-gu-avatar-raw-dev/$bucketPath1",
      s"fs:com-gu-avatar-incoming-dev/$bucketPath1"
    )

    deleted.getOrElse(null).resources should contain only (expected:_*)
    fileStore.files.exists { case (path, _) => path.contains(bucketPath1) } shouldBe false
    fileStore.files.exists { case (path, _) => path.contains(bucketPath2) } shouldBe false
  }

  "cleaning a user" should "remove all non-active avatars" in new WithStore {
    val avatar1 = uploadAvatar("first")
    avatarStore.updateStatus(avatar1.id, Approved)

    // Note, because cleaning happens automatically now on status updates, we
    // have to artificially do setup
    val bytes = "myimage".getBytes
    val avatar2 = avatar1.copy(id = "foobar", status = Approved, isActive = false)
    kvStore.put(storeProps.kvTable, avatar2)
    fileStore.put(storeProps.fsRawBucket, KVLocationFromID(avatar2.id), bytes, new ObjectMetadata())
    fileStore.put(storeProps.fsIncomingBucket, KVLocationFromID(avatar2.id), bytes, new ObjectMetadata())
    fileStore.put(storeProps.fsProcessedBucket, KVLocationFromID(avatar2.id), bytes, new ObjectMetadata())

    val bucketPath = KVLocationFromID(avatar2.id)

    val cleaned = avatarStore.cleanupInactive(User(userId))
    val expected = List(
      s"kv:${avatar2.id}",
      s"fs:com-gu-avatar-processed-dev/$bucketPath",
      s"fs:com-gu-avatar-raw-dev/$bucketPath",
      s"fs:com-gu-avatar-incoming-dev/$bucketPath"
    )

    cleaned.getOrElse(null).resources should contain only (expected:_*)
    fileStore.files.exists { case (path, _) => path.contains(bucketPath) } shouldBe false
  }

  // Historically, all avatars were preserved so it is possible to have many
  "getAll" should "return all avatars for a user" in new WithStore {
    val avatarProto = Avatar(
      "9f3450f-fc24-400a-9ceb-9b347d95646e5e",
      "http://avatar-url-1",
      "735437",
      "foo.gif",
      "http://avatar-raw-url1",
      Approved,
      new DateTime(),
      new DateTime(),
      isSocial = false,
      isActive = false
    )

    (1 to 50).foreach { _ =>
      val avatar = avatarProto.copy(id = util.Random.nextString(10))
      kvStore.put(storeProps.kvTable, avatar)
    }

    avatarStore.getAll(User(avatarProto.userId))
      .map(_.body)
      .getOrElse(Nil)
      .size shouldBe 50
  }
}
