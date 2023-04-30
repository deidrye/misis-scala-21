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

import scala.concurrent.{ExecutionContext, Future}

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
                  new Service(Account.stateList.size + 1)
                  complete(StatusCodes.OK, newState)
              }
          } ~
          path("account" / IntNumber / "amount") { accountId =>
              get {
                  val accountService = new Service(accountId)
                  complete(StatusCodes.OK, s"Account $accountId amount: ${accountService.getAmount}")
              }
          } ~
          path("account" / IntNumber / "credit" / IntNumber) { (accId, amount) =>
              post {
                  val accountService = new Service(accId)
                  onSuccess(accountService.CreditOrDebit(amount, "credit", "defaultCategory")) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(value) => complete(StatusCodes.OK, "OK")
                  }
              }
          }~
          path("account" / IntNumber / "debit" / IntNumber / Segment.?) { (accId, amount, category) =>
              post {

                  val accountService = new Service(accId)
                  onSuccess(accountService.CreditOrDebit(amount, "debit", category.getOrElse("defaultCategory"))) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(value) => complete(StatusCodes.OK, "OK")
                  }
              }
          }~
          path("account" / IntNumber / "transfer" / IntNumber / IntNumber) { (accId1, accId2, amount) =>
              post {
                  val accountService1 = new Service(accId1)
                  val accountService2 = new Service(accId2)
                  onSuccess(accountService1.CreditOrDebit(amount, "debit", "defaultCategory")) {
                      case Left(message) => complete(StatusCodes.BadRequest, message)
                      case Right(_) => onSuccess(accountService2.CreditOrDebit(amount, "credit", "defaultCategory")) {
                          case Left(message) => complete(StatusCodes.BadRequest, message)
                          case Right(_) => complete(StatusCodes.OK, "OK")
                      }
                  }
              }
          }



}
