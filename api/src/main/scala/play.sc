import com.gu.adapters.queue.DeletionMessage
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{DefaultFormats, Formats}
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.Try

case class DeletionMessage(userId: String)

case class DeletionEvent(Message: String) {
  import DeletionEvent._
  def userId: Option[String] = {
    for {
      messageJson <- parseOpt(Message)
      deletionMessage <- messageJson.extractOpt[DeletionMessage]
    } yield deletionMessage.userId
  }
}

object DeletionEvent {
  implicit val jsonFormats: Formats = DefaultFormats

  def deletionEvent(event: String): Option[String] = {
    for {
      deletionEventJson <- parseOpt(event)
      deletionEvent <- deletionEventJson.extractOpt[DeletionEvent]
      userId <- deletionEvent.userId
    } yield userId
  }
}


val m =
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
    |  "SigningCertURL" : "https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-433026a4050d206028891664da859041.pem",
    |  "UnsubscribeURL" : "https://sns.eu-west-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-1:942464564246:com-gu-identity-account-deletions-PROD:86af3451-fd48-404a-9f14-5cf7deb99f9d"
    |}
  """.stripMargin

DeletionEvent.deletionEvent(m)

println("Done")