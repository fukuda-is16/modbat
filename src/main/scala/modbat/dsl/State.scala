package modbat.dsl

import modbat.cov.StateCoverage
import modbat.log.Log
import modbat.mbt.{MBT, MessageHandler}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class State (val name: String) {
  override def toString = name
  var coverage: StateCoverage = _

  var instanceNum = 0
  var feasibleInstances: Map[Transition, Int] = Map.empty
  def feasible = instanceNum > 0 && !feasibleInstances.isEmpty
  @volatile var waitingInstances: Map[Int, Int] = Map.empty//key: id, value: (instanceNum,disabled)
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
  def waitingInstanceNum: Int = {
    var num = 0
    waitingInstances.foreach(i => num = num + i._2)
    num
  }
  def disabled(id: Int): Boolean = !waitingInstances.contains(id)
  def disableTimeout = synchronized {
    if(!waitingInstances.isEmpty) {
      Log.debug("disabled timeout in " + this.toString)
//      if(real) reduceRealInst(waitingInstanceNum)
      waitingInstances = Map.empty
    }
  }
  private def availableTransitions: List[(Transition)] = transitions.filter({t => t.subTopic.isEmpty && t.waitTime.isEmpty})
  def addTransition(t: Transition) = {
    if(transitions.filter(_ == t).isEmpty) {
      transitions = t :: transitions
      Log.debug(s"(state ${this.toString}) registered transition ${t.toString}")

      if(!t.waitTime.isEmpty) {
        if(!timeout.isEmpty) if(timeout.get.waitTime != t.waitTime) {
          Log.error(s"There should be at most one timeout transition in state ${toString}.")
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
    Log.info(s"$instanceNum instance(s) are in state ${this.toString}.")
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
      case Some((x, y)) => 
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

  def registerToScheduler(t:Transition, time: Int, n: Int) {
    if(n > 0) {
      val id = getId
      synchronized {
        waitingInstances = waitingInstances + (id -> n)
      }
      val task = new TimeoutTask(t, n, id)
      //if(real) addRealInst(n)
      Log.debug(s"registered task to execute ${t.toString} for $n instances in $time millis")
      if(real) MBT.time.scheduler.scheduleOnceWithRealDelay(time.millis)(task.run())
      else MBT.time.scheduler.scheduleOnce(time.millis)(task.run())
    }
  }

  class TimeoutTask(t: Transition, n: Int, id: Int) extends Thread {
    override def run() {
      if(!disabled(id)) {
        Log.debug(s"add timeout transition ${t.toString} to feasibleInstances")
        addFeasibleInstances(t, n)
//        if(real) reduceRealInst(n)
      }
      synchronized {
        waitingInstances = waitingInstances - id
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
    Log.debug(s"(state $toString) waitingInstanceNum = $waitingInstanceNum, freeInstanceNum = $freeInstanceNum")
    cancelFeasibleInstances
    disableTimeout
    addFeasibleInstances(trans, instanceNum)
    freeInstanceNum = 0
  }

  def clear = {
    transitions = List.empty
    instanceNum = 0
    freeInstanceNum = 0
    disableTimeout
  }
}
