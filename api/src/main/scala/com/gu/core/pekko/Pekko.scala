package com.gu.core.pekko

import org.apache.pekko.actor.{ActorSystem, Scheduler}
import org.apache.pekko.dispatch.MessageDispatcher
import org.apache.pekko.stream.Materializer

import scala.concurrent.{ExecutionContextExecutor, Future}

object Pekko {
  implicit val system: ActorSystem = ActorSystem()
  implicit val mat: Materializer = Materializer(system)
  implicit val defaultDispatcher: ExecutionContextExecutor = system.dispatcher
  val scheduler: Scheduler = system.scheduler

  private val blockingOperations: MessageDispatcher = system.dispatchers.lookup("pekko.blocking-operations")

  def executeBlocking[T](block: => T): Future[T] = {
    Future(block)(blockingOperations)
  }
}
