package com.gu.adapters.notifications

import java.util.concurrent.Executors

import com.amazonaws.ClientConfiguration
import com.amazonaws.services.sns.AmazonSNSAsyncClient
import com.gu.adapters.store.AWSCredentials

object SNSClient {
  lazy val location = "sns.eu-west-1.amazonaws.com"
  val snsClient = new AmazonSNSAsyncClient(AWSCredentials.awsCredentials, new ClientConfiguration(), Executors.newFixedThreadPool(10))
  snsClient.setEndpoint(location)
}
