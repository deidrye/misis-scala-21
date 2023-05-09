package ru.misis.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import ru.misis.model.Account
import ru.misis.service.Service
import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.DefaultJsonProtocol._

import scala.concurrent.ExecutionContext

class Routes(service: Service)(implicit val system: ActorSystem, val executionContext: ExecutionContext) {

    private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
    implicit val stateFormat = jsonFormat2(Account.State)

    val routes: Route =
        path("accounts") {
            get {
                complete(StatusCodes.OK, service.getStates)
            }
        } ~
          path("create") {
              post {
                  val newState = service.create
                  complete(StatusCodes.OK, newState)
              }
          } ~
          path("account" / IntNumber / "amount") { accountId =>
              get {
                  //complete(StatusCodes.OK, s"Account $accountId amount: ${accountService.getAmount}")
                  onSuccess(service.getAmount(accountId)) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(value) => complete(StatusCodes.OK, s"Account $accountId amount: ${service.getAmount(accountId)}")
                  }
              }
          } ~
          path("account" / IntNumber / "credit" / IntNumber) { (accId, amount) =>
              post {
                  onSuccess(service.CreditOrDebit(accId, amount, "credit", None)) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(value) => complete(StatusCodes.OK, "OK")
                  }
              }
          }~
          path("account" / IntNumber / "debit" / IntNumber / Segment.?) { (accId, amount, category) =>
              post {

                  onSuccess(service.CreditOrDebit(accId, amount, "debit", category)) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(value) => complete(StatusCodes.OK, "OK")
                  }
              }
          }~
          path("account" / IntNumber / "transfer" / IntNumber / IntNumber) { (accId1, accId2, amount) =>
              post {

                  onSuccess(service.CreditOrDebit(accId1, amount, "debit", None)) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(_) => onSuccess(service.CreditOrDebit(accId2, amount, "credit", None)) {
                          case Left(message) => complete(StatusCodes.BadRequest, message)
                          case Right(_) => complete(StatusCodes.OK, "OK")
                      }
                  }
              }
          }



}
