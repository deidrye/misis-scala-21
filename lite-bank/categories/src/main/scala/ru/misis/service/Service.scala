package ru.misis.service

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import org.slf4j.LoggerFactory
import ru.misis.model.Account.AccountUpdated
import ru.misis.util.WithKafka

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

case class Category(name: String, percent: Int)

class Service()(implicit val system: ActorSystem, executionContext: ExecutionContext) extends WithKafka {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val categories = mutable.Map[String, Category](
    "easy" -> Category("Easy", 5),
    "hard" -> Category("Hard", 10)
  )

  import ru.misis.model.ModelJsonFormats._

  def categoryList: List[Category] = categories.values.toList

  def addCategory(name: String, percent: Int): Category = {
    val category = Category(name, percent)
    categories.put(name, category)
    category
  }



  kafkaSource[AccountUpdated]
    .filter(event => event.category.isDefined)
    .map { event =>
      val categoryOpt = categories.get(event.category.get)
      if (categoryOpt.isDefined) {
        val category = categoryOpt.get
        val value = -event.value * category.percent / 100
        AccountUpdated(event.accountId, value)
      } else {
        logger.info(s"Transaction type (${event.category.get}) has no cashback")
        AccountUpdated(event.accountId, 0)
      }
    }
    .runWith(kafkaSink)
    .map(_ => ())
}

