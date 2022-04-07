package com.gu.logstash

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.util.EC2MetadataUtils
import com.gu.adapters.config.Config
import logstash.{LogbackConfig, LogbackOperationsPool}

import scala.concurrent.ExecutionContext
import com.gu.auth.AWSCredentials
import com.typesafe.scalalogging.LazyLogging

case class LogStashConf(
  enabled: Boolean,
  stream: String,
  region: String,
  awsCredentialsProvider: AWSCredentialsProvider,
  customFields: Map[String, String])

class LogstashLifecycle(
  playConfig: Config,
  logbackOperationsPool: LogbackOperationsPool)(implicit executionContext: ExecutionContext) {
  def start(): Unit = {
    new Logstash(logbackOperationsPool).init(playConfig)
  }
}

class Logstash(logbackOperationsPool: LogbackOperationsPool) extends LazyLogging {

  def customFields(stage: String): Map[String, String] = Map(
    "stack" -> "discussion",
    "app" -> "avatar-api",
    "stage" -> stage,
    "ec2_instance" -> Option(EC2MetadataUtils.getInstanceId).getOrElse("Not running on ec2")
  )

  def makeLogstashConfig(config: Config, enabled: Boolean): LogStashConf = {
    LogStashConf(
      enabled,
      config.elkConfig.streamName,
      config.elkConfig.region,
      AWSCredentials.awsCredentials,
      customFields(config.elkConfig.stage)
    )
  }

  def init(config: Config): Unit = {
    if (config.elkConfig.enabled && config.elkConfig.stage != "DEV") {
      new LogbackConfig(logbackOperationsPool).init(makeLogstashConfig(config, enabled = true))
    } else {
      logger.info("Not logging to ELK (DEV mode or disabled in config)")
    }
  }
}