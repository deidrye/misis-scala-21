package ru.misis.service

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.slf4j.LoggerFactory
import ru.misis.event.Event
import ru.misis.model.Account
import ru.misis.model.Account.{AccountUpdated, State}
import ru.misis.util.WithKafka

import scala.concurrent.{ExecutionContext, Future}

class Service(accountId: Int)(implicit val system: ActorSystem, val executionContext: ExecutionContext) extends WithKafka {

  import ru.misis.model.ModelJsonFormats._

  private val logger = LoggerFactory.getLogger(this.getClass)


  def getAmount(accId: Int): Future[Either[String, Event]] = {
    val state: State = State(accId, getStateById(accId).map(_.amount).getOrElse(0))
    if (getStateById(accId).isDefined) Future.successful(Left(s"State $accId amount = ${state.amount}"))

    else Future.successful(Left(s"Не существует счёта с идентификатором $accId"))
  }

  def CreditOrDebit(accId: Int, cash: Int, transaction: String, category: Option[String]): Future[Either[String, Event]] = {
    if (transaction == "debit") update(accId, -cash, category)
    else update(accId, cash, category)

  }

  def update(accId: Int, value: Int, category: Option[String]): Future[Either[String, Event]] = {
    val state: State = State(accId, getStateById(accId).map(_.amount).getOrElse(0))
    if (state.amount + value < 0)
      Future.successful(Left("Недостаточно средств на счете"))
    else {
      val event = AccountUpdated(state.id, value, category)
      publishEvent(event)
        .map(_ => {
          Right(event)
        })
        .recover { case ex => Left(ex.getMessage) }
    }
  }


  def create(): State = {
    Account.create()
  }

  def getStates: List[State] = {
    Account.stateList
  }

  def getStateById(stateId: Int): Option[State] = {
    Account.stateList.find(_.id == stateId)
  }

  kafkaCSource[AccountUpdated]
    .filter {
      case (_, AccountUpdated(id, _,  _, _)) =>
        getStateById(id).isDefined
      case (_, event) =>
        logger.info(s"Empty account $event")
        false
    }
    .map { case message @ (_, AccountUpdated(id, value,  category, _)) =>
      val state: State = State(id, getStateById(id).map(_.amount).getOrElse(0))
      state.update(value)
      logger.info(s"State updated $value $state")
      message
    }
    .filter { case (_, AccountUpdated(_, _, _, needCommit)) => needCommit.getOrElse(false)}
    .map { case (offset, _) => offset }
    .log("AccountUpdated error")
    .runWith(committerSink)
}
