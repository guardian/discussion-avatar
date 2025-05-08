package com.gu.adapters.queue

import com.gu.core.models.{User, UserDeleted, UserDeletionFailed}
import com.gu.core.store.AvatarStore
import org.apache.pekko.stream.connectors.sqs.MessageAction
import org.mockito.Mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures

import software.amazon.awssdk.services.sqs.model.Message

import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar

class SqsDeleteQueueTest extends AnyFlatSpec with Matchers with MockitoSugar with ScalaFutures {

  trait DeletionEventHandlerScope {
    val eventHandlerProps = SqsDeletionConsumerProps("queueUrl", "region")
    val avatarStore = mock[AvatarStore]

    val messageBody =
      """
        |{
        |  "Type" : "Notification",
        |  "MessageId" : "d8392d1e-8c11-5a2b-ab02-7fd3dc51552b",
        |  "TopicArn" : "arn:aws:sns:eu-west-1:942464564246:com-gu-identity-account-deletions-PROD",
        |  "Subject" : "Account deletion event",
        |  "Message" : "{\"userId\":\"18467226\",\"eventType\":\"DELETE\"}",
        |  "Timestamp" : "2018-01-08T10:54:56.297Z",
        |  "SignatureVersion" : "1",
        |  "Signature" : "Ytm4fKPLA/UgvXdpVbS5/TRFGOEelEE8LkSd2lf+Hf5ecbXkdyI0DxISVSYn6Jc4QWHvdFdIxG9vWNltzaWCcXb85HDXS30aeMOb2Im+T9+3obl8c/9jlNoWvCO+KfruFlTl7/y/GM7E+PYvrNuvhiD1eBFg2PTVF/1iPgUlNnmSjQPgWM29aht3MYj6O4uWGofWOgJcef5clbuPdLdzZiUb0b8xnWiMb0wuCH7l3+Xqs3yF/pBrk6i0AZb9rlgWgzRamhK+Iq9nMIuL0qoKZbY+uBX4uv8EDhMidIeGnJFtngxDijD9t35R1OZE5t1pS+93Hmjz5owVNJBMdukJkA==",
        |  "SigningCertURL" : "blah",
        |  "UnsubscribeURL" : "blah"
        |}
      """.stripMargin

    val message = Message.builder().body(messageBody).build()

  }

  "DeletionEventHandler" should "delete users from avatar store and Ack" in new DeletionEventHandlerScope {
    when(avatarStore.deleteAll(User("18467226"))) thenReturn Right(UserDeleted(User("18467226"), List.empty))
    whenReady(SqsDeletionConsumer.deleteUser(message, avatarStore), Timeout(5 seconds))(_ shouldBe MessageAction.Delete(message))
    verify(avatarStore).deleteAll(User("18467226"))
  }

  it should "should requeue with delay on error" in new DeletionEventHandlerScope {
    when(avatarStore.deleteAll(User("18467226"))) thenReturn Left((UserDeletionFailed("blah", List("blah"))))
    whenReady(SqsDeletionConsumer.deleteUser(message, avatarStore), Timeout(5 seconds))(_ shouldBe MessageAction.Ignore(message))
    verify(avatarStore).deleteAll(User("18467226"))
  }

}
