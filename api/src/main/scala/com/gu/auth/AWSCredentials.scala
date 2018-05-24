package com.gu.auth

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}

object AWSCredentials {
  val awsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("discussion"),
    new DefaultAWSCredentialsProviderChain()
  )
}