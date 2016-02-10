package actors

import java.io.File

import TestSpec._
import actors.ClientFSM.{errorHappened, executed, work}
import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import java.nio.ByteBuffer
import java.io.FileInputStream
import java.nio.channels.FileChannel.MapMode._

import util.control.Breaks._
import scalaxb.{DataRecord, ParserFailure}

import org.sikuli.script._
import org.sikuli.basics.Debug

import scala.util.control.NonFatal

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
        // do something

//        val spec : String = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<test name=\"OpenEditor\"  xmlns=\"http://www.example.org/Sikuliator\">\n<!-- <click similarity=\"0.6\">VMC.Workbench.MapViewer</click>\n<click similarity=\"0.6\">VMC.MapViewer.startEouting</click> -->\n<enterText>\n\t<text>Hallo</text>\n</enterText>\n</test>"
        val spec = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<spec:test name=\"OpenEditor\"  xmlns:spec=\"http://www.example.org/Sikuliator\">\n<!-- <click similarity=\"0.6\">VMC.Workbench.MapViewer</click>\n<click similarity=\"0.6\">VMC.MapViewer.startEouting</click> -->\n<spec:enterText>\n\t<spec:text>Hallo</spec:text>\n</spec:enterText>\n</spec:test>"
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
                  case a :SequencableOption =>
                    throw new UnsupportedOperationException(""+a.toString)
                }
              }
              success = true
              builder.append("finished successfully")
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
            output.append("entering text: "+in)
          case _ =>
            throw new UnsupportedOperationException("")
        }
      case Some("useInput") =>
        input match
      {
        case Some(inputString:String) =>
          region.`type`(inputString)
          output.append("entering text: "+inputString)
        case None =>
          throw new IllegalArgumentException("useInput without input")
      }
      case None | _ =>
        throw new UnsupportedOperationException("")
    }
  }

}