package controllers

import actors.ClientFSM
import play.api.mvc._
import akka.actor._
import javax.inject._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future


@Singleton
class Application @Inject() (system: ActorSystem) extends Controller {

  val fsmActor = system.actorOf(ClientFSM.props, "clientFSM")

  def status = Action.async
  {
    implicit  request =>
      {
        Future{ Ok ("")}
      }
  }
//  def projects = Action.async {
//    implicit request =>
//      {
//        val projectStubs = ProjectsRepository.getAllProjectStubs
//        projectStubs.flatMap( (stubs : Seq[ProjectStub]) => Future{
//          val jsonStubs = stubs.toList.map((stub : ProjectStub) => Json.toJson(stub))
//          Ok (Json.prettyPrint(JsonHelper.concatAsJsonArray(jsonStubs)))
//        })
//      }
//
//  }



}
