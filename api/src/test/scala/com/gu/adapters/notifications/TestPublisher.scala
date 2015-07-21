package com.gu.adapters.notifications

import scala.concurrent.Future

class TestPublisher extends Publisher {
  def publish(arn: String, msg: String, subject: String): Future[String] = {
    Future.successful("123")
  }
}
