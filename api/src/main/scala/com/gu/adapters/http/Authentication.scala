package com.gu.adapters.http

import cats.effect.IO
import cats.implicits._
import com.gu.adapters.config.IdentityConfig
import com.gu.core.models.Errors._
import com.gu.core.models.{Error, User}
import com.gu.identity.auth.{AccessToken, IdapiAuthConfig, IdapiAuthService, IdapiUserCredentials, OktaAudience, OktaIssuerUrl, OktaLocalAccessTokenValidator, OktaTokenValidationConfig, AccessScope => IdentityAccessScope}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.Uri

import java.util.concurrent.{Executors, ThreadPoolExecutor, TimeUnit}
import scala.concurrent.ExecutionContext

object TokenAuth {

  def isValidKey(
    authHeader: Option[String],
    apiKeys: List[String]
  ): Either[Error, String] = {

    val tokenHeader = "Bearer token="
    val token = authHeader
      .withFilter(_.startsWith(tokenHeader))
      .map(_.stripPrefix(tokenHeader))

    val tokenOrError = token match {
      case Some(valid) if apiKeys.contains(valid) => Right(valid)
      case Some(_) => Left("Invalid access token provided")
      case None => Left("No access token in request")
    }
    tokenOrError.leftMap(error => tokenAuthorizationFailed(List(error)))
  }
}

object AccessScope {

  /**
   * Allows the client to read the user's saved for later articles
   */
  case object readSelf extends IdentityAccessScope {
    val name = "guardian.avatar-api.read.self"
  }

  /**
   * Allows the client to update the user's saved for later articles
   */
  case object updateSelf extends IdentityAccessScope {
    val name = "guardian.avatar-api.update.self"
  }
}

class AuthenticationService(
  idapiAuthService: IdapiAuthService,
  oktaLocalValidator: OktaLocalAccessTokenValidator
) {
  private def authenticateUserWithIdapi(
    scGuUCookie: Option[String]
  ): Either[Error, User] = {
    // Attempt to authenticate user.
    val result = for {
      value <- IO.fromEither(
        Either.fromOption(
          scGuUCookie,
          new Exception("No secure cookie in request")
        )
      )
      credentials = IdapiUserCredentials.SCGUUCookie(value)
      identityId <- idapiAuthService.authenticateUser(credentials)
    } yield identityId

    result
      .redeem(
        err =>
          Left(userAuthorizationFailed(List(err.getMessage)): Error),
        identityId => Right(User(identityId))
      )
      .unsafeRunSync()
  }

  private def authenticateUserWithOkta(
    accessToken: Option[String],
    identityAccessScope: IdentityAccessScope
  ): Either[Error, User] = {
    // attempt to authenticate user with oauth tokens
    val result = for {
      token <- accessToken.toRight(
        oauthTokenAuthorizationFailed(
          List("No oauth access token in request"),
          400
        )
      )
      credentials = AccessToken(token.stripPrefix("Bearer "))
      claims <- oktaLocalValidator
        .parsedClaimsFromAccessToken(credentials, List(identityAccessScope))
        .left
        .map(e =>
          oauthTokenAuthorizationFailed(
            List(e.message),
            e.suggestedHttpResponseCode
          )
        )
    } yield claims.identityId

    // determine result
    result match {
      case Left(err) => Left(err)
      case Right(identityId) => Right(User(identityId))
    }
  }

  def authenticateUser(
    scGuUCookie: Option[String],
    accessToken: Option[String] = None,
    identityAccessScope: IdentityAccessScope
  ): Either[Error, User] = {
    // check if scGuUCookie or accessToken is present and determine correct method to uuse
    if (accessToken.isDefined) {
      // if access token present, use okta to authenticate
      authenticateUserWithOkta(accessToken, identityAccessScope)
    } else if (scGuUCookie.isDefined) {
      // if only scGuUCookie is present, use idapi to authenticate
      authenticateUserWithIdapi(scGuUCookie)
    } else {
      // if neither are present, return error
      Left(userAuthorizationFailed(
        List("No secure cookie or access token in request")
      ))
    }
  }
}

object AuthenticationService extends LazyLogging {

  object AuthenticationServiceThreadPoolMonitorer extends LazyLogging {

    private val scheduler = Executors.newScheduledThreadPool(1)

    def monitorThreadPool(threadPoolExecutor: ThreadPoolExecutor): Unit = {
      scheduler.scheduleAtFixedRate(
        () => {
          logger.info(s"identity API thread pool stats: $threadPoolExecutor")
        },
        60,
        60,
        TimeUnit.SECONDS
      )
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
    val threadPool = Executors
      .newFixedThreadPool(blockingThreads)
      .asInstanceOf[ThreadPoolExecutor]
    AuthenticationServiceThreadPoolMonitorer.monitorThreadPool(threadPool)

    // Access token that's 'safe' to log e.g secret_token -> sec**********
    // Assumes size of  token significantly greater than size 3.
    val scrubbedAccessToken = config.accessToken.zipWithIndex.map {
      case (c, i) => if (i < 3) c else '*'
    }.mkString

    val uri = Uri.unsafeFromString(config.apiUrl)

    // Log parameters to be sure they are correct.
    logger.info(
      s"initialising identity auth service - url: $uri, access token: $scrubbedAccessToken"
    )

    implicit val ec: ExecutionContext =
      ExecutionContext.fromExecutorService(threadPool)

    val idapiAuthConfig = IdapiAuthConfig(
      identityApiUri = uri,
      accessToken = config.accessToken
    )

    val oktaLocalConfig = OktaTokenValidationConfig(
      OktaIssuerUrl(config.oktaIssuer),
      Some(OktaAudience(config.oktaAudience)),
      clientId = None
    )

    val identityAuthService = IdapiAuthService.unsafeInit(idapiAuthConfig)

    val oktaLocalValidator = OktaLocalAccessTokenValidator
      .fromConfig(oktaLocalConfig)
      .getOrElse(throw new NoSuchElementException("Cannot configure validator"))

    new AuthenticationService(identityAuthService, oktaLocalValidator)
  }
}
