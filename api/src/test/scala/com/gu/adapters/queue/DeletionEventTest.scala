package com.gu.adapters.queue

import org.scalatest.{FlatSpec, Matchers}

class DeletionEventTest extends FlatSpec with Matchers {

  "DeletionEvent" should "deserialise deletion event correctly" in {
    val eventData =
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

    DeletionEvent.deletionEvent(eventData) shouldBe Some("18467226")

  }

  it should "return None when no user id available" in {
    val eventData =
      """
        |{
        |  "Type" : "Notification",
        |  "MessageId" : "d8392d1e-8c11-5a2b-ab02-7fd3dc51552b",
        |  "TopicArn" : "arn:aws:sns:eu-west-1:942464564246:com-gu-identity-account-deletions-PROD",
        |  "Subject" : "Account deletion event",
        |  "Message" : "",
        |  "Timestamp" : "2018-01-08T10:54:56.297Z",
        |  "SignatureVersion" : "1",
        |  "Signature" : "Ytm4fKPLA/UgvXdpVbS5/TRFGOEelEE8LkSd2lf+Hf5ecbXkdyI0DxISVSYn6Jc4QWHvdFdIxG9vWNltzaWCcXb85HDXS30aeMOb2Im+T9+3obl8c/9jlNoWvCO+KfruFlTl7/y/GM7E+PYvrNuvhiD1eBFg2PTVF/1iPgUlNnmSjQPgWM29aht3MYj6O4uWGofWOgJcef5clbuPdLdzZiUb0b8xnWiMb0wuCH7l3+Xqs3yF/pBrk6i0AZb9rlgWgzRamhK+Iq9nMIuL0qoKZbY+uBX4uv8EDhMidIeGnJFtngxDijD9t35R1OZE5t1pS+93Hmjz5owVNJBMdukJkA==",
        |  "SigningCertURL" : "blah",
        |  "UnsubscribeURL" : "blah"
        |}
      """.stripMargin

    DeletionEvent.deletionEvent(eventData) shouldBe None

  }

  it should "return None when json is invalid" in {
    val eventData =
      """
        |}
      """.stripMargin

    DeletionEvent.deletionEvent(eventData) shouldBe None

  }

}
