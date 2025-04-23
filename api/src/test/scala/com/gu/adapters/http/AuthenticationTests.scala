package com.gu.adapters.http

import cats.effect.IO
import com.gu.core.models.{Errors, User}
import com.gu.identity.auth._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class AuthenticationTests extends FunSuite with Matchers with MockitoSugar {

  trait Mocks {
    val idapiAuthService: IdapiAuthService = mock[IdapiAuthService]
    val oktaLocalValidator: OktaLocalAccessTokenValidator = mock[OktaLocalAccessTokenValidator]
    val authenticationService = new AuthenticationService(idapiAuthService, oktaLocalValidator)
  }

  test("Decode cookie") {
    new Mocks {
      val scGuUCookie = "sc-gu-u-cookie"
      val credentials = IdapiUserCredentials.SCGUUCookie(scGuUCookie)
      when(idapiAuthService.authenticateUser(credentials)).thenReturn(IO("identity-id"))
      authenticationService.authenticateUser(
        Some(scGuUCookie),
        None,
        AccessScope.readSelf
      ) shouldBe Right(User("identity-id"))
    }
  }

  test("Reject invalid cookie") {
    new Mocks {
      val scGuUCookie = "sc-gu-u-cookie"
      val credentials = IdapiUserCredentials.SCGUUCookie(scGuUCookie)
      when(idapiAuthService.authenticateUser(credentials)).thenReturn(IO.raiseError(new Exception("invalid cookie")))
      authenticationService.authenticateUser(
        Some(scGuUCookie),
        None,
        AccessScope.readSelf
      ) shouldBe Left(Errors.userAuthorizationFailed(List("invalid cookie")))
    }
  }

  test("Don't authenticate a user with no cookie or token") {
    new Mocks {
      authenticationService.authenticateUser(
        None,
        None,
        AccessScope.readSelf
      ) shouldBe Left(Errors.userAuthorizationFailed(List("No secure cookie or access token in request")))
    }
  }

  test("Decode OAuth access token") {
    new Mocks {
      val token = "token"
      when(oktaLocalValidator.parsedClaimsFromAccessToken(AccessToken(token), List(AccessScope.readSelf))).thenReturn(Right(DefaultAccessClaims("oktaId", "test@test.com", "123", None)))
      authenticationService.authenticateUser(None, Some(s"Bearer $token"), AccessScope.readSelf) shouldBe Right(User("123"))
    }
  }

  test("Reject invalid access token") {
    new Mocks {
      val token = "token"
      when(oktaLocalValidator.parsedClaimsFromAccessToken(AccessToken(token), List(AccessScope.readSelf))).thenReturn(Left(InvalidOrExpiredToken))
      authenticationService.authenticateUser(
        None, Some(s"Bearer $token"),
        AccessScope.readSelf
      ) shouldBe Left(Errors.oauthTokenAuthorizationFailed(List("Token is invalid or expired"), 401))
    }
  }
}
