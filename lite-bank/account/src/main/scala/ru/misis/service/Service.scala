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

    private var state: State = State(accountId, 0)

    if (getStateById(accountId).isDefined) {
        val newAmount = getStateById(accountId).map(_.amount).getOrElse(0)
        state = State(accountId, newAmount)
    }
    else {
        state = state
    }

    def getAmount: Int = state.amount

    def CreditOrDebit(cash: Int, transaction: String, category: String): Future[Either[String, Event]] = {
        if (transaction == "debit") update(-cash, category)
        else update(cash, category)

    }

    def update(value: Int, category: String): Future[Either[String, Event]] = {
        if (state.amount + value < 0)
            Future.successful(Left("Недостаточно средств на счете"))
        else {
            val cashbackPercent = category match {
                case "category1" => 5
                case "category2" => 10
                case "category3" => 15
                case _ => 0
            }
            val total = value - ((value * cashbackPercent) / 100)
            val event = AccountUpdated(state.id, total)
            publishEvent(event)
              .map(_ => {
                  state = state.update(total)
                  Right(event)
              })
              .recover { case ex => Left(ex.getMessage) }
        }
    }


    def snapshot(): Future[Unit] = {
        val amount = getAmount
        Source(Seq(
            AccountUpdated(state.id, - amount, None, Some(true)),
            AccountUpdated(state.id, + amount)
        )).runWith(kafkaSink).map(_ => ())
    }

    def create(): State = {
        val newState = Account.create()
        if (newState.id == accountId) {
            state = newState
        }
        newState
    }

    def getStates: List[State] = {
        Account.stateList
    }

    def getStateById(stateId: Int): Option[State] = {
        Account.stateList.find(_.id == stateId)
    }

    kafkaCSource[AccountUpdated]
      .filter {
          case (_, AccountUpdated(id, _, _, needCommit)) if id == state.id =>
              needCommit.getOrElse(false)
          case (_, event) =>
              logger.info(s"Empty account ${event}")
              false
      }
      .map {
          case (offset, AccountUpdated(_, value, _, _)) =>
              state = state.update(value)
              logger.info(s"State updated ${value} ${state}")
              offset
      }
      .log("AccountUpdated error")
      .runWith(committerSink)

}
