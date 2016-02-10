package actors

import java.io.{BufferedOutputStream, FileOutputStream, File, FileInputStream}

import TestSpec._
import actors.ClientFSM.{errorHappened, executed, work}
import actors.WorkFetcherActor.Mapping
import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import java.nio.ByteBuffer
import java.nio.channels.FileChannel.MapMode._

import play.api.libs.ws.WS

import scala.concurrent.{Future, Await}
import scala.util.{Success, Try, Failure}
import util.control.Breaks._
import scalaxb.{DataRecord, ParserFailure}

import org.sikuli.script._
import org.sikuli.basics.Debug
import play.api.Play.current
import scala.util.control.NonFatal

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.collection.JavaConverters._

import scala.concurrent.duration._
/**
  * Created by Malte on 09.02.2016.
  */
object TestExecutionActor {
  def props = Props[TestExecutionActor]
}


class TestExecutionActor  extends Actor
{
  import TestExecutionActor._

  override def receive: Receive =
  {
    case newWork : work =>
      {
        val fsm = sender()

        val spec = newWork.spec
        val builder = new StringBuilder(200)
        try
          {
            val obj = scalaxb.fromXML[TestSpec.Test](xml.XML.loadString(spec))
            Debug.setDebugLevel(3)
            val screen = Sikulix.init()
            if (screen == null)
              {
                throw new RuntimeException()
              }
            val windowRegion = new Region(screen.getBounds)
            var success = false
            breakable{

            val iter = obj.sequencableoption.iterator
            while(iter.hasNext)
              {
                val entry = iter.next()
                entry.value  match
                  {
                  case EnterText(entertextoption: scalaxb.DataRecord[Any]) =>
                    enterText(windowRegion,builder,entertextoption,None)
                  case click:Click =>
                    tryToClick(windowRegion,builder,click.value,fetchImage(click.value,newWork.referenceImageMapping),click.similarity,click.towardsTop,click.towardsRight) match
                      {
                      case Failure =>
                        break()
                      case _ =>
                    }
                  case waitForSomeImage : WaitForSomeImage =>
                    val timeout = waitForSomeImage.secondsToWait.map(_ seconds).getOrElse(Duration.Inf)
                    val futureResult = tryToFind(windowRegion,builder,waitForSomeImage.value,fetchImage(waitForSomeImage.value,newWork.referenceImageMapping),waitForSomeImage.similarity)
                    try {
                    val bestMatch = Await.result(futureResult,timeout)
                      // maybe store it
                      builder.append("found "+waitForSomeImage.value+"\n")
                    }
                      catch {
                        case NonFatal(e) =>
                          break()
                      }
                  case a :SequencableOption =>
                    throw new UnsupportedOperationException(""+a.toString)
                }
              }
              success = true
              builder.append("finished successfully\n")
            }

            val finalScreenshotpath = screen.capture().getFile
            screen.find(finalScreenshotpath).highlight(2)
            val file = new File(finalScreenshotpath)
            val message = builder.toString()
            fsm ! new executed(success,message,file)

          }
            catch
              {
                case e : ParserFailure =>
                  {
                    fsm ! new errorHappened("error while parsing")
                  }
                case e : UnsupportedOperationException =>
                {
                  fsm ! new errorHappened("error while executing: unsupported operation - " + e.toString)
                }
                case e : IllegalArgumentException =>
                  {
                    fsm ! new errorHappened("error while executing: bad spec - " + e.toString)
                  }
                case NonFatal(e)
                => {
                  fsm ! new errorHappened("error while executing")
                }
              }




      }
  }


  def enterText(region: Region, output: StringBuilder, enterText: scalaxb.DataRecord[Any], input :Option[String]) =
  {
    enterText.key match
      {
      case Some("text") =>
        enterText.value match
          {
          case in:String =>
            region.`type`(in)
            output.append("entering text: "+in+"\n")
          case _ =>
            throw new UnsupportedOperationException("")
        }
      case Some("useInput") =>
        input match
      {
        case Some(inputString:String) =>
          region.`type`(inputString)
          output.append("entering text: "+inputString+"\n")
        case None =>
          throw new IllegalArgumentException("useInput without input")
      }
      case None | _ =>
        throw new UnsupportedOperationException("")
    }
  }

  def tryToClick(region: Region, output: StringBuilder, name: String,filePath: String, similarity: Double, towardsTop: Option[Int],towardsRight: Option[Int]) =
  {
    val matches = region.findAll(filePath)
      if(matches.hasNext)
        {
          val bestMatch = matches.asScala.maxBy(_.getScore)
          if (bestMatch.getScore < similarity)
            {
              output.append("no match was found for "+name+": best match was: "+bestMatch.getScore+" and not "+similarity+" \n")
              Failure( new IllegalStateException("clicking failed"))
            }
          else
            {
              if(bestMatch.click() ==1)
                {
                  output.append("clicked on "+name+"\n")
                  Success
                }
              else
                {
                  Failure( new IllegalStateException("clicking failed"))
                }
            }
        }
    else
        {
          output.append("no match was found for "+name+"\n")
          Failure( new IllegalStateException("clicking failed"))
        }
  }

  def tryToFind(region: Region, output: StringBuilder, name: String,filePath: String, similarity: Double) : Future[Match] =
  Future
  {
    val matches = region.findAll(filePath)
    if(matches.hasNext)
    {
      val bestMatch = matches.asScala.maxBy(_.getScore)
      if (bestMatch.getScore < similarity)
      {
        output.append("no match was found for "+name+": best match was: "+bestMatch.getScore+" and not "+similarity+" \n")
        throw new IllegalStateException("find failed")
      }
      else
      {
        bestMatch
      }
    }
    else
    {
      output.append("no match was found for "+name+"\n")
      throw new IllegalStateException("find failed")
    }
  }


  def fetchImage(name:String, mapping: Seq[Mapping]) : String =
  {
    Await.result(WS.url(mapping.find(_.name == name).get.url).get().map((response) =>
      {
        val tmpFile = File.createTempFile("ref_", ".png")
        tmpFile.deleteOnExit()
        val bos = new BufferedOutputStream(new FileOutputStream(tmpFile))
        Stream.continually(bos.write(response.bodyAsBytes))
        bos.close()
        tmpFile.getAbsolutePath
      }
    ),20 seconds)
  }

}