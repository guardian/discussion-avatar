package com.gu.auth

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import software.amazon.awssdk.auth.credentials.{ AwsCredentialsProviderChain => V2AwsCredentialsProviderChain, ProfileCredentialsProvider => V2ProfileCredentialsProvider, DefaultCredentialsProvider => V2DefaultCredentialsProvider }

object AWSCredentials {
  val awsCredentials = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("discussion"),
    new DefaultAWSCredentialsProviderChain())
  val awsCredentialsV2 = V2AwsCredentialsProviderChain.builder()
    .addCredentialsProvider(V2ProfileCredentialsProvider.create("discussion"))
    .addCredentialsProvider(V2DefaultCredentialsProvider.create())
    .build()
}