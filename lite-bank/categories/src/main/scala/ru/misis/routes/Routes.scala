package ru.misis.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import ru.misis.service.Service
import ru.misis.model.ModelJsonFormats
import spray.json.DefaultJsonProtocol._
import ru.misis.model.Account
import spray.json._
import ru.misis.service.Category

class Routes(service: Service)(implicit val system: ActorSystem) {
    private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
    implicit val categoryFormat = jsonFormat2(Category)

    val routes: Route =
        path("categories") {
            get {
                complete(StatusCodes.OK, service.categoryList)
            }
        }~
          path("create" / "category" / Segment / IntNumber) { (name, percent) =>
              post {
                  complete(StatusCodes.OK, service.addCategory(name, percent))
              }
          }
}
