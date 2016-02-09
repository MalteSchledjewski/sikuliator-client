package actors

import java.io.File

import actors.ClientFSM.{executed, work}
import akka.actor.{Props, Actor}
import akka.actor.Actor.Receive
import java.nio.ByteBuffer
import java.io.FileInputStream
import java.nio.channels.FileChannel.MapMode._

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
        val file = new File("D:\\tmp\\Tagbaum - Einrichtungen.png")
//        val image : ByteBuffer = new FileInputStream(file).getChannel.map(READ_ONLY,0,file.length())
        val success = true
        val message = ""
        fsm ! new executed(success,message,file)
      }
  }


}