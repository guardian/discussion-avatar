package com.gu.adapters.http

import java.util.concurrent.{Executors, ThreadPoolExecutor, TimeUnit}

import cats.effect.IO
import cats.implicits._
import com.gu.adapters.config.IdentityConfig
import com.gu.core.models.Errors._
import com.gu.core.models.{Error, User}
import com.gu.identity.auth.{IdentityAuthService, UserCredentials}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.Uri
import scalaz.Scalaz._
import scalaz.{-\/, NonEmptyList, \/, \/-}

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

  def authenticateUser(scGuUCookie: Option[String]): Error \/ User = {
    // Attempt to authenticate user.
    val result = for {
      value <- IO.fromEither(
        Either.fromOption(
          scGuUCookie,
          new Exception("No secure cookie in request")
        )
      )
      credentials = UserCredentials.SCGUUCookie(value)
      identityId <- identityAuthService.authenticateUser(credentials)
    } yield identityId

    // Convert authentication result to return type
    // (identity-auth-core uses cats (Either); discussion-avatar uses scalaz (\/)).
    result.redeem(
      err => -\/(userAuthorizationFailed(NonEmptyList(err.getMessage)): Error),
      identityId => \/-(User(identityId))
    ).unsafeRunSync()
  }
}


object AuthenticationService extends LazyLogging {

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

    // Access token that's 'safe' to log e.g secret_token -> sec**********
    // Assumes size of  token significantly greater than size 3.
    val scrubbedAccessToken = config.accessToken.zipWithIndex
      .map { case (c, i) => if (i < 3) c else '*' }
      .toString

    val uri = Uri.unsafeFromString(config.apiUrl)

    // Log parameters to be sure they are correct.
    logger.info(s"initialising identity auth service - url: $uri, access token: $scrubbedAccessToken")

    implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)
    val identityAuthService = IdentityAuthService.unsafeInit(
      identityApiUri = uri,
      accessToken = config.accessToken
    )

    new AuthenticationService(identityAuthService)
  }
}