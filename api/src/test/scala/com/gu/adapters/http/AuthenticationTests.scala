package com.gu.adapters.http

import cats.effect.IO
import com.gu.core.models.{Errors, User}
import com.gu.identity.auth.{IdapiAuthService, IdapiUserCredentials}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSuite, Matchers}
import scalaz.{-\/, NonEmptyList, \/-}

class AuthenticationTests extends FunSuite with Matchers with MockitoSugar {

  trait Mocks {
    val idapiAuthService: IdapiAuthService = mock[IdapiAuthService]
    val authenticationService = new AuthenticationService(idapiAuthService)
  }

  test("Decode cookie") {
    new Mocks {
      val scGuUCookie = "sc-gu-u-cookie"
      val credentials = IdapiUserCredentials.SCGUUCookie(scGuUCookie)
      when(idapiAuthService.authenticateUser(credentials)).thenReturn(IO("identity-id"))
      authenticationService.authenticateUser(Some(scGuUCookie)) shouldBe \/-(User("identity-id"))
    }
  }

  test("Reject invalid cookie") {
    new Mocks {
      val scGuUCookie = "sc-gu-u-cookie"
      val credentials = IdapiUserCredentials.SCGUUCookie(scGuUCookie)
      when(idapiAuthService.authenticateUser(credentials)).thenReturn(IO.raiseError(new Exception("invalid cookie")))
      authenticationService.authenticateUser(Some(scGuUCookie)) shouldBe -\/(Errors.userAuthorizationFailed(NonEmptyList("invalid cookie")))
    }
  }

  test("Don't authenticate a user with no cookie") {
    new Mocks {
      authenticationService.authenticateUser(None) shouldBe -\/(Errors.userAuthorizationFailed(NonEmptyList("No secure cookie in request")))
    }
  }
}