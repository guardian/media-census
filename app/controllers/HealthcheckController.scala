package controllers

import javax.inject.{Inject, Singleton}
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import responses.PingResponse
import io.circe.generic.auto._
import io.circe.syntax._

@Singleton
class HealthcheckController @Inject() (cc:ControllerComponents) extends AbstractController(cc) with Circe {

  def healthcheck = Action {
    Ok(PingResponse("ok").asJson)
  }

}
