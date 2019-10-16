package com.gu.adapters.http

import java.util.concurrent.{Executors, ThreadPoolExecutor, TimeUnit}

import com.gu.adapters.config.IdentityConfig
import com.gu.core.models.Errors._
import com.gu.core.models.{Error, User}
import com.gu.core.utils.ErrorHandling.attempt
import com.gu.identity.auth.IdentityAuthService
import com.gu.identity.cookie.GuUDecoder
import com.typesafe.scalalogging.LazyLogging
import org.http4s.Uri
import scalaz.Scalaz._
import scalaz.{NonEmptyList, \/}

import scala.concurrent.ExecutionContext

object TokenAuth {

  def isValidKey(authHeader: Option[String], apiKeys: List[String]): Error \/ String = {

    val tokenHeader = "Bearer token="
    val token = authHeader.withFilter(_.startsWith(tokenHeader)).map(_.stripPrefix(tokenHeader))

    val tokenOrError = token match {
      case Some(valid) if apiKeys.contains(valid) => valid.right
      case Some(invalid) => "Invalid access token provided".left
      case None => "No access token in request".left
    }
    tokenOrError.leftMap(error => tokenAuthorizationFailed(NonEmptyList(error)))
  }
}

class AuthenticationService(identityAuthService: IdentityAuthService) {

  def userFromCookie(decoder: GuUDecoder, cookie: Option[String]): Error \/ User = {
    val authedUser = for {
      c <- cookie.toRightDisjunction("No secure cookie in request")
      user <- readCookie(decoder, c)
    } yield user

    authedUser.leftMap(error => userAuthorizationFailed(NonEmptyList(error)))
  }

  private[this] def readCookie(decoder: GuUDecoder, cookie: String): String \/ User = {
    for {
      user <- attempt(decoder.getUserDataForScGuU(cookie))
        .toOption.flatten.toRightDisjunction("Unable to extract user data from cookie")
    } yield User(user.id)
  }
}


object AuthenticationService {

  object AuthenticationServiceThreadPoolMonitorer extends LazyLogging {

    private val scheduler = Executors.newScheduledThreadPool(1)

    def monitorThreadPool(threadPoolExecutor: ThreadPoolExecutor): Unit = {
      scheduler.scheduleAtFixedRate(() => {
        logger.info(s"identity API thread pool stats: $threadPoolExecutor")
      }, 60, 60, TimeUnit.SECONDS)
    }
  }

  def fromIdentityConfig(config: IdentityConfig): AuthenticationService = {
    // discussion-api uses a thread pool of 30 threads for the authentication service
    // and the monitoring of that indicates that this is more than sufficient:
    // when stats are logged there are no active threads and no queued tasks.
    // Since calls to identity API will be a lot less frequent in this application,
    // 10 should be more than sufficient, but the stats can be monitored and adjusted accordingly.
    val blockingThreads = 10

    // ExecutorService returned is a ThreadPoolExecutor.
    // Explicitly cast to this type so that thread pool can be monitored
    // (e.g. get access to active thread count etc).
    val threadPool = Executors.newFixedThreadPool(blockingThreads).asInstanceOf[ThreadPoolExecutor]
    AuthenticationServiceThreadPoolMonitorer.monitorThreadPool(threadPool)

    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)
    val identityAuthService = IdentityAuthService.unsafeInit(
      identityApiUri = Uri.unsafeFromString(config.apiUrl),
      accessToken = config.accessToken
    )

    new AuthenticationService(identityAuthService)
  }
}