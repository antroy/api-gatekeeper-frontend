/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package unit.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import config.WSHttp
import connectors.ApplicationConnector
import model.{FetchApplicationsFailed, PreconditionFailed, ApproveUpliftSuccessful}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Matchers}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

class ApplicationConnectorSpec extends UnitSpec with Matchers with ScalaFutures with WiremockSugar with BeforeAndAfterEach with WithFakeApplication {

  trait Setup {
    val authToken = "Bearer Token"
    implicit val hc = HeaderCarrier().withExtraHeaders(("Authorization", authToken))

    val connector = new ApplicationConnector {
      override val http = WSHttp
      override val applicationBaseUrl: String = wireMockUrl
    }
  }

  "approveUplift" should {
    "send Authorisation and return OK if the uplift was successful on the backend" in new Setup {
      val applicationId = "anApplicationId"
      val gatekeeperId = "loggedin.gatekeeper"
      stubFor(post(urlEqualTo(s"/application/$applicationId/approve-uplift")).willReturn(aResponse().withStatus(204)))
      val result = await(connector.approveUplift(applicationId, gatekeeperId))
      verify(1, postRequestedFor(urlPathEqualTo(s"/application/$applicationId/approve-uplift"))
        .withHeader("Authorization", equalTo(authToken))
        .withRequestBody(equalTo( s"""{"gatekeeperUserId":"$gatekeeperId"}""")))

      result shouldBe ApproveUpliftSuccessful
    }

    "handle 412 precondition failed" in new Setup {
      val applicationId = "anApplicationId"
      val gatekeeperId = "loggedin.gatekeeper"
      stubFor(post(urlEqualTo(s"/application/$applicationId/approve-uplift")).willReturn(aResponse().withStatus(412)
        .withBody( """{"code"="INVALID_STATE_TRANSITION","message":"Application is not in state 'PENDING_GATEKEEPER_APPROVAL'"}""")))

      intercept[PreconditionFailed](await(connector.approveUplift(applicationId, gatekeeperId)))

      verify(1, postRequestedFor(urlPathEqualTo(s"/application/$applicationId/approve-uplift"))
        .withHeader("Authorization", equalTo(authToken))
        .withRequestBody(equalTo( s"""{"gatekeeperUserId":"$gatekeeperId"}""")))
    }
  }

  "rejectUplift" should {
    "send Authorisation and return Ok if the uplift rejection was successful on the backend" in new Setup {
      val applicationId = "anApplicationId"
      val gatekeeperId = "loggedin.gatekeeper"
      val rejectionReason = "A similar name is already taken by another application"
      stubFor(post(urlEqualTo(s"/application/$applicationId/reject-uplift")).willReturn(aResponse().withStatus(204)))

      val result = await(connector.rejectUplift(applicationId, gatekeeperId, rejectionReason))

      verify(1, postRequestedFor(urlPathEqualTo(s"/application/$applicationId/reject-uplift"))
        .withHeader("Authorization", equalTo(authToken))
        .withRequestBody(equalTo(
          s"""{"gatekeeperUserId":"$gatekeeperId","reason":"$rejectionReason"}""")))
      }

    "hande 412 preconditions failed" in new Setup {
      val applicationId = "anApplicationId"
      val gatekeeperId = "loggedin.gatekeeper"
      val rejectionReason = "A similar name is already taken by another application"
      stubFor(post(urlEqualTo(s"/application/$applicationId/reject-uplift")).willReturn(aResponse().withStatus(412)
        .withBody( """{"code"="INVALID_STATE_TRANSITION","message":"Application is not in state 'PENDING_GATEKEEPER_APPROVAL'"}""")))

      intercept[PreconditionFailed](await(connector.rejectUplift(applicationId, gatekeeperId, rejectionReason)))

      verify(1, postRequestedFor(urlPathEqualTo(s"/application/$applicationId/reject-uplift"))
        .withHeader("Authorization", equalTo(authToken))
        .withRequestBody(equalTo(
          s"""{"gatekeeperUserId":"$gatekeeperId","reason":"$rejectionReason"}""")))
    }
  }

  "fetchApplications" should {
    "retrieve all applications pending uplift approval" in new Setup {
      stubFor(get(urlEqualTo(s"/gatekeeper/applications")).willReturn(aResponse().withStatus(200)
        .withBody("[]")))

      val result = await(connector.fetchApplications())

      verify(1, getRequestedFor(urlPathEqualTo("/gatekeeper/applications"))
        .withHeader("Authorization", equalTo(authToken)))
    }

    "propagate FetchApplicationsFailed exception" in new Setup {
      stubFor(get(urlEqualTo(s"/gatekeeper/applications")).willReturn(aResponse().withStatus(500)))

      intercept[FetchApplicationsFailed](await(connector.fetchApplications()))

      verify(1, getRequestedFor(urlPathEqualTo(s"/gatekeeper/applications"))
        .withHeader("Authorization", equalTo(authToken)))
    }
  }
}
