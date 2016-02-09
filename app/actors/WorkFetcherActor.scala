package actors

import javax.inject.Inject
import actors.ClientFSM.{work, no_work}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.ws
import play.api.Play.current
import scala.concurrent.Future

import com.google.inject.assistedinject.Assisted
import play.api.mvc._
import play.api.libs.ws._
import javax.inject.Inject
import akka.actor.Actor.Receive
import akka.actor.{Props, Actor}
import play.api._
import play.api.libs.ws.WSClient
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.ws.ning.NingWSClient
/**
  * Created by Malte on 09.02.2016.
  */



object WorkFetcherActor
{
  def props = Props[WorkFetcherActor]
  final case class FetchInitialWork()

//  def createRequest(url :String) : WSRequest =
//  {
//    WS.url(url)
//  }
}
//@Inject() (ws: WSClient)

class WorkFetcherActor  extends Actor {
  import WorkFetcherActor._

  val wsClient = NingWSClient()

  override def receive: Receive =
  {
    case FetchInitialWork() =>
      {
        val fsm = sender()
        val request: WSRequest = wsClient.url("http://localhost:9000/getWork")
        request.get().map(
          response =>
            {
              (response.json \ "work").toOption match
              {
                case None =>
                  {
                    fsm ! new no_work()
                  }
                case Some(work : JsValue) =>
                  {
                    val executionId : Long =( work \ "executionId" ).as[Long]
                    val testId : Long =( work \ "testId" ).as[Long]
                    val testVersionId : Long =( work \ "testVersionId" ).as[Long]
                    val executionStep : Long =( work \ "executionStep" ).as[Long]
                    val spec : String =( work \ "spec" ).as[String]
                    fsm ! new work(executionId, testId , testVersionId , executionStep, spec)
                  }
              }
            }
        )
      }

  }
}
