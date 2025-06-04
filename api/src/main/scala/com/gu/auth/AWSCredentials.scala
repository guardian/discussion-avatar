package com.gu.auth

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

object AWSCredentials {
  val awsCredentials = AwsCredentialsProviderChain.builder()
    .addCredentialsProvider(ProfileCredentialsProvider.create("discussion"))
    .addCredentialsProvider(DefaultCredentialsProvider.create())
    .build()
}