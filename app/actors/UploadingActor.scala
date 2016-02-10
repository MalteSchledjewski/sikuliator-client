package actors

import java.io.{File, ByteArrayOutputStream}
import java.nio.ByteBuffer
import actors.ClientFSM.{unsuccessful_results_uploaded, successful_results_uploaded, errorHappened}
import play.api.Play.current

import akka.actor.Actor.Receive
import akka.actor.{Actor, Props}
import com.ning.http.client.FluentCaseInsensitiveStringsMap
import com.ning.http.client.multipart.{FilePart, StringPart, Part}
import com.ning.http.client.providers.jdk.MultipartRequestEntity
import play.api.http.Writeable
import play.api.libs.ws.{WSRequest, WS, WSResponse}
import play.api.libs.ws.ning.NingWSClient
import scala.collection.JavaConverters._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.util.{Success, Failure}

/**
  * Created by Malte on 09.02.2016.
  */
object UploadingActor {
  def props = Props[UploadingActor]
}

class UploadingActor  extends Actor{

  val fullUrl = "http://localhost:9000/projects/1/resultImages"


  override def receive: Receive =
  {
    case upload : Result =>
      {
        val fsm = sender()
        postMultipart(fullUrl, "resultImage", upload.image, "image/png", "" + upload.executionId + " " + upload.testId+ "").onComplete {
          case Failure(e) => {
            fsm ! errorHappened("upload failed")
          }
          case Success(resp: WSResponse) => {
            Console.println(resp.body)
            (resp.json \ "url").toOption match
              {
              case None =>
                fsm ! errorHappened("upload failed returned body was: " + resp.body)
              case Some(url) =>
                {
                  val request: WSRequest = WS.url("http://localhost:9000/postResult")
                  val parts : List[Part] = List(
                    new StringPart("url", "http://localhost:9000"+url),
                    new StringPart("executionId", upload.executionId.toString),
                    new StringPart("executionStep", upload.executionStep.toString),
                    new StringPart("message", upload.message),
                    new StringPart("testId", upload.testId.toString)
                  )
                  val mpre = new MultipartRequestEntity(parts.asJava, new FluentCaseInsensitiveStringsMap)
                  val baos = new ByteArrayOutputStream(5 * 1024 * 1024)
                  mpre.writeRequest(baos)
                  val bytes = baos.toByteArray
                  request
                    .withHeaders("Accept" -> "application/json", "Content-Type" -> mpre.getContentType)
                    .post(bytes)(Writeable.wBytes)
                      .onComplete(
                        {
                          case Failure(e) =>
                            fsm ! errorHappened("upload failed")
                          case Success(res) =>
                            {
                              if (res.status != 200)
                                {
                                  fsm ! errorHappened("upload failed")
                                }
                              else
                              {
                                upload.success match
                                {
                                  case true =>
                                    fsm ! successful_results_uploaded()
                                  case false =>
                                    fsm ! unsuccessful_results_uploaded()
                                }
                              }
                            }
                        }
                      )

                }
            }

          }
        }
      }
  }


  def postMultipart(requestUrl: String,
                    fileName: String, file: File, fileMimeType: String,
                    name: String): Future[WSResponse] = {


    val parts : List[Part] = List(
      new FilePart(fileName, file, fileMimeType),
    new StringPart("type", fileMimeType),
    new StringPart("name", name)
    )


    val mpre = new MultipartRequestEntity(parts.asJava, new FluentCaseInsensitiveStringsMap)
    val baos = new ByteArrayOutputStream(5 * 1024 * 1024)
    mpre.writeRequest(baos)
    val bytes = baos.toByteArray



      WS.url(requestUrl)
        .withHeaders("Accept" -> "application/json", "Content-Type" -> mpre.getContentType)
//        .withQueryString(stringParts: _*)
        .post(bytes)(Writeable.wBytes)

  }
}
