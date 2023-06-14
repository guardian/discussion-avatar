package com.gu.adapters.http

import cats.effect.IO
import com.gu.core.models.{Errors, User}
import com.gu.identity.auth.{DefaultAccessClaims, DefaultAccessClaimsParser, IdapiAuthService, IdapiUserCredentials, InvalidOrExpiredToken, OktaLocalValidator}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import scalaz.{-\/, NonEmptyList, \/-}

class AuthenticationTests extends FunSuite with Matchers with MockitoSugar {

  trait Mocks {
    val idapiAuthService: IdapiAuthService = mock[IdapiAuthService]
    val oktaLocalValidator: OktaLocalValidator = mock[OktaLocalValidator]
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
      ) shouldBe \/-(User("identity-id"))
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
      ) shouldBe -\/(Errors.userAuthorizationFailed(NonEmptyList("invalid cookie")))
    }
  }

  test("Don't authenticate a user with no cookie or token") {
    new Mocks {
      authenticationService.authenticateUser(
        None,
        None,
        AccessScope.readSelf
      ) shouldBe -\/(Errors.userAuthorizationFailed(NonEmptyList("No secure cookie or access token in request")))
    }
  }

  test("Decode OAuth access token") {
    new Mocks {
      val token = "token"
      when(oktaLocalValidator.parsedClaimsFromAccessToken(
        token,
        List(AccessScope.readSelf),
        DefaultAccessClaimsParser
      )).thenReturn(Right(DefaultAccessClaims("test@test.com", "123", None)))
      authenticationService.authenticateUser(None, Some(s"Bearer $token"), AccessScope.readSelf) shouldBe \/-(User("123"))
    }
  }

  test("Reject invalid access token") {
    new Mocks {
      val token = "token"
      when(oktaLocalValidator.parsedClaimsFromAccessToken(
        token,
        List(AccessScope.readSelf),
        DefaultAccessClaimsParser
      )).thenReturn(Left(InvalidOrExpiredToken))
      authenticationService.authenticateUser(
        None, Some(s"Bearer $token"),
        AccessScope.readSelf
      ) shouldBe -\/(Errors.oauthTokenAuthorizationFailed(NonEmptyList("Token is invalid or expired"), "401"))
    }
  }
}