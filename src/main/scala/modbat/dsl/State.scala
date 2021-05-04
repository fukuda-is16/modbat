package modbat.dsl

import modbat.cov.StateCoverage
import modbat.log.Log
import modbat.mbt.{MBT, MessageHandler}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import accsched._

class State (val name: String) {
  override def toString = name
  var coverage: StateCoverage = _

  var instanceNum = 0
  var feasibleInstances: Map[Transition, Int] = Map.empty
  def feasible = instanceNum > 0 && !feasibleInstances.isEmpty

  //@volatile var waitingInstances: Map[Int, Int] = Map.empty//key: id, value: (instanceNum,disabled)
  val timeoutTaskIDs = scala.collection.mutable.Set[Int]()

  var freeInstanceNum = 0 //instances with no feasible transition
  var transitions: List[Transition] = List.empty
  var subTopics: Map[String, Transition] = Map.empty //topics subscribing in this state
  var messageBuffer: String = ""
  var timeSlice = 11
  var timeoutId = 0
  var model: MBT = _
  var timeout: Option[Transition] = None
  var real = false //if the timeout is real (=true) or virtual (=false)
/*
  def addRealInst(n: Int = 1) = {
    if(MBT.realInst == 0) MBT.realMillis = System.currentTimeMillis()
    MBT.realInst += n
  }
  def reduceRealInst(n: Int = 1) = {
    MBT.realInst -= n
    if(MBT.realInst < 0) {
      Log.error("real time tasks shouldn't be negative")
      System.exit(1)
    }
  }
*/
  def getId = {
    timeoutId += 1
    timeoutId
  }
  def addFeasibleInstances(t: Transition, n: Int) = {
    if(n > 0) {
      if(feasibleInstances.contains(t)) {
        feasibleInstances = feasibleInstances.updated(t, feasibleInstances(t) + n)
      } else {
        feasibleInstances = feasibleInstances.updated(t, n)
      }
      Log.debug(s"(state $toString) added ${t.toString} ($n  instances) to feasibleInstances")
    }
    else Log.debug(s"(state $toString) no instance to add")
  }
  def cancelFeasibleInstances = {
    feasibleInstances = Map.empty
  }

  def disableTimeout(): Unit = {
    timeoutTaskIDs.synchronized {
      for(taskid <- timeoutTaskIDs) {
        AccSched.cancelSchedule(taskid)
      }
      timeoutTaskIDs.clear()
    }
  }

  private def availableTransitions: List[(Transition)] = transitions.filter({t => t.subTopic.isEmpty && t.waitTime.isEmpty})
  def addTransition(t: Transition) = {
    if(transitions.filter(_ == t).isEmpty) {
      transitions = t :: transitions
      Log.debug(s"(state ${this.toString}) registered transition ${t.toString}")

      if(!t.waitTime.isEmpty) {
        if(!timeout.isEmpty) if(timeout.get.waitTime.get() != t.waitTime.get()) {
          Log.error(s"There should be at most one timeout transition in state ${toString}.")
          Log.error(s"${timeout.get.waitTime.get()}, ${t.waitTime.get()}")
          System.exit(1)
        }
        this.timeout = Some(t)
        real = t.real
      }
      t.subTopic match {
        case Some(topic) =>
          subTopics = subTopics + (topic -> t)
          Log.debug(s"(state ${this.toString}) registering topic $topic (transition ${t.toString})")
          MessageHandler.regTopic(topic, this)
        case _ => 
      }
    }
  }

  def viewTransitions = {
    var s = this.toString + ".transitions = "
    transitions.foreach(s += _.toString + ", ")
    Log.debug(s)
  }
  def totalWeight(trans: List[Transition]) = {
    var w = 0.0
    for (t <- trans) {
      w = w + t.action.weight
    }
    w
  }

  def subTrans(topic: String): Map[String, Transition] = {
    var m: Map[String, Transition] = Map.empty
    for(t <- transitions) {
      t.subTopic match {
        case Some(topic) => m = m + (topic -> t)
        case _ =>
      }
    }
    m
  }
  def reduceInstances(n: Int) = {
    instanceNum -= n
    if(instanceNum < 0)
      Log.error(s"(state $toString) number of instances is negative")
  }

  //Assign instances. If no transition is available and timeout is setted, register instances to scheduler.
  def assignInstances(n: Int) = {
    instanceNum += n
    Log.debug(s"$instanceNum instance(s) are in state ${this.toString}.")
    if(!availableTransitions.isEmpty) {
      assignFreeInstToTrans(n)
    } else {
      timeout match {
        case Some(t) =>
          Log.debug("assign timeout")
          assignTimeout(t,n)
        case None => 
          freeInstanceNum += n
          Log.debug(s"(state ${this.toString}) freeInstanceNum = $freeInstanceNum")
      }
    }
  }
  def assignFreeInstToTrans(n: Int) {
    var remain = n
    var s = "(state " + this.toString + ") availableTransitions: "
    for(tr <- availableTransitions) {
      s = s + tr.toString+","
    }
    val totalW = totalWeight(availableTransitions)
    val rnd = scala.util.Random.shuffle(availableTransitions)
    for(t <- rnd) {
      val tN = (n * t.action.weight / totalW).toInt
      if(tN > 0) {
        addFeasibleInstances(t, tN)
        remain = remain - tN
      }
    }
    addFeasibleInstances(rnd.head, remain)
  }

  def assignTimeout(t:Transition, n: Int) {
    t.action.waitTime match {
      case Some(f) =>
        val (x, y) = f()
        if(x == y) {
          registerToScheduler(t, x, n)
        } else {
          val width = (y - x) / (timeSlice - 1)
          val dividedN = (n / timeSlice).toInt
          val l = scala.util.Random.shuffle(List.range(0,timeSlice))
          val (l1, _) = l.splitAt(n - dividedN * timeSlice)
          for(i <- 0 to timeSlice - 1) {
            val remain = if(l1.contains(i)) 1 else 0
            registerToScheduler(t, x + (i * width).toInt, dividedN + remain)
          }
        }
      case None => 
    }
  }

  def registerToScheduler(t:Transition, time: Long, n: Int): Unit = {
    if(n > 0) {
      if (time <= 0) {
        addFeasibleInstances(t, n)
        return
      }
      val task = new TimeoutTask(t, n)
      Log.debug(s"registered task to execute ${t.toString} for $n instances in $time millis")
      val taskid = AccSched.schedule({
        task.execute()
      }, time, real)
      task.taskid = taskid
      timeoutTaskIDs.synchronized {
        timeoutTaskIDs += taskid
      }
    }
  }
// waitingInstances -> timeoutTaskIDs (Set[taskID]) 
// メッセージが来たら全部消す
// 来ずにtask実行で消す
  class TimeoutTask(t: Transition, n: Int) {
    var taskid: Int = -1
    def execute() {
      Log.debug(s"add timeout transition ${t.toString} to feasibleInstances")
      addFeasibleInstances(t, n)

      timeoutTaskIDs.synchronized {
        timeoutTaskIDs -= taskid
      }
    }
  }

  /*
   * called when an MQTT message arrives
   * cancel timeout and assigen instances to the subscription-triggered transition
   */
  def messageArrived(topic: String, msg: String) {
    messageBuffer = msg
    var trans = subTopics(topic)
    Log.debug(s"(state ${this.toString} messageArrived) message arrived from topic $topic: $msg")
    //Log.debug(s"(state $toString) waitingInstanceNum = $waitingInstanceNum, freeInstanceNum = $freeInstanceNum")
    cancelFeasibleInstances
    disableTimeout()
    addFeasibleInstances(trans, instanceNum)
    freeInstanceNum = 0
  }

  def clear = {
    transitions = List.empty
    timeout = None
    instanceNum = 0
    freeInstanceNum = 0
    disableTimeout
  }
}
