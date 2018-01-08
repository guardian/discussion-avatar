package com.gu.core.akka

import akka.actor.{ActorSystem, Scheduler}
import akka.dispatch.MessageDispatcher
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}

object Akka {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val defaultDispatcher: ExecutionContextExecutor = system.dispatcher
  val scheduler: Scheduler = system.scheduler

  private val blockingOperations: MessageDispatcher = system.dispatchers.lookup("akka.blocking-operations")

  def executeBlocking[T](block: => T): Future[T] = {
    Future(block)(blockingOperations)
  }
}
