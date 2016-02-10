package actors

import java.io.File

import actors.WorkFetcherActor.{Mapping, FetchInitialWork}
import akka.actor.{Props, ActorRef, FSM}
import play.api.Logger
import play.api.libs.ws.WSClient
import scala.concurrent.duration._



// states
sealed trait State
case object Initial extends State
case object Executing extends State
case object UploadingResults extends State
case object FetchingWork extends State
case object Dirty extends State

sealed trait Data
case object Uninitialized extends Data
final case class Work(executionId : Long, testId : Long, testVersionId : Long, executionStep: Long, spec:String, referenceImageMapping : Seq[Mapping], sequenceVersions : Seq[Mapping]) extends Data
final case class Result(executionId : Long, testId : Long, testVersionId : Long, executionStep: Long, success : Boolean, message:String, image : File) extends Data
final case class ExecutionStep(executionId : Long, testId : Long, testVersionId : Long, executionStep: Long) extends Data
final case class ErrorMessage(message :String) extends Data
/**
  * Created by Malte on 09.02.2016.
  */

object ClientFSM {
  def props = Props[ClientFSM]
  // events
  final case class work(executionId : Long, testId : Long, testVersionId : Long, executionStep: Long, spec:String, referenceImageMapping : Seq[Mapping], sequenceVersions : Seq[Mapping])
  final case class no_work()
  final case class executed(success : Boolean, message:String, image : File)
  final case class errorHappened(message :String)
  final case class successful_results_uploaded()
  final case class unsuccessful_results_uploaded()
  final case class no_further_work()
  final case class got_work()
}

class ClientFSM  extends FSM[State, Data]{
  import ClientFSM._
  // create other actors
  val workFetcher = context.actorOf(WorkFetcherActor.props, name = "WorkFetcherActor")
  val testExecuter = context.actorOf(TestExecutionActor.props, name = "TestExecuterActor")
  val uploader = context.actorOf(UploadingActor.props, name = "UploadExecuterActor")


  Console.println("ClientFSM starts")
  startWith(Initial,Uninitialized)

  when(Initial,stateTimeout = 10 second)
  {
    case Event( nothing : no_work ,Uninitialized) =>
    {
      goto(Initial) using Uninitialized
    }
    case Event(StateTimeout ,Uninitialized) =>
      {
        workFetcher ! FetchInitialWork()
        stay()
      }
    case Event(newWork :work, Uninitialized) =>
      {
        Console.println("Go to Executing")
        testExecuter ! newWork
        goto(Executing) using Work(newWork.executionId, newWork.testId, newWork.testVersionId, newWork.executionStep, newWork.spec, newWork.referenceImageMapping, newWork.sequenceVersions)
      }
  }

  when(Executing)
  {
    case Event(exe : executed, work : Work) =>
      {
        // start uploading
        val result = new Result(work.executionId,work.testId,work.testVersionId,work.executionStep,exe.success,exe.message,exe.image)
        uploader ! result
        goto(UploadingResults) using result
      }
  }

when(UploadingResults)
  {
    case Event(upload : unsuccessful_results_uploaded , any) =>
      {
        goto(Dirty)
      }

    case Event(upload : successful_results_uploaded, any) =>
      {
        goto(Dirty) // change this to FetchingWork
      }
  }

  when(Dirty)
  {
    case Event(e, s) =>
      {
        stay
      }
  }



  whenUnhandled {
    case Event(err : errorHappened , any) =>
    {
      goto(Dirty) using ErrorMessage(err.message)
    }
    case Event(e, s) =>
      Console.println("received unhandled request" ,e, "in state ", stateName,"/", s)
      stay
  }

  initialize()
}
