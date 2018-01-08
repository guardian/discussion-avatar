package com.gu.adapters.queue

import akka.stream.alpakka.sqs.{Ack, RequeueWithDelay}
import com.amazonaws.services.sqs.model.Message
import com.gu.core.models.{Error, User, UserDeleted, UserDeletionFailed}
import com.gu.core.store.AvatarStore
import org.scalatest.concurrent.{Futures, ScalaFutures}
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration._
import scalaz.{NonEmptyList, \/}

class DeletionEventHandlerTest extends FlatSpec with Matchers with MockitoSugar with ScalaFutures {

  trait DeletionEventHandlerScope {
    val eventHandlerProps = DeletionEventProps("queueUrl", "region")
    val avatarStore = mock[AvatarStore]
    val deletionEventHandler = new DeletionEventHandler(eventHandlerProps, avatarStore)

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

    val message = mock[Message]
    when(message.getBody) thenReturn messageBody
  }

  "DeletionEventHandler" should "delete users from avatar store and Ack" in new DeletionEventHandlerScope {
    when(avatarStore.deleteAll(User("18467226"), isDryRun = false)) thenReturn \/.right(UserDeleted(User("18467226"), List.empty))
    whenReady(deletionEventHandler.deleteUser(message), Timeout(5 seconds)) (_ shouldBe Ack())
    verify(avatarStore).deleteAll(User("18467226"), isDryRun = false)
  }

  it should "should requeue with delay on error" in new DeletionEventHandlerScope {
    when(avatarStore.deleteAll(User("18467226"), isDryRun = false)) thenReturn \/.left(UserDeletionFailed("blah", NonEmptyList("blah")))
    whenReady(deletionEventHandler.deleteUser(message), Timeout(5 seconds)) (_ shouldBe RequeueWithDelay(10))
    verify(avatarStore).deleteAll(User("18467226"), isDryRun = false)
  }

}
