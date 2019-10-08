package modbat.dsl

import modbat.cov.StateCoverage
import modbat.log.Log
import modbat.mbt.MBT
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class State (val name: String) {
  override def toString = name
  var coverage: StateCoverage = _

  var instanceNum = 0
  var feasibleInstances: List[(Transition, Int)] = List.empty//Map[Transition, Int]
  var waitingInstances: Map[Int, (Int, Boolean)] = Map.empty//key: id, value: (instanceNum,disabled)
  var transitions: List[Transition] = List.empty
  var timeSlice = 10//slices we make when waiting for timeout
  var timeoutId = 0

  def getId = {
    timeoutId += 1
    timeoutId
  }
  def waitingInstanceNum(id: Int): Int = waitingInstances(id)._1
  def disabled(id: Int): Boolean = waitingInstances(id)._2
  def disable = waitingInstances.foreach({i => (i._1,true)})
  def availableTransitions: List[(Transition)] = transitions.filter({t => t.subTopic.isEmpty && t.waitTime.isEmpty})
  def addTransition(tr: Transition) = {
    transitions = tr +: transitions
  }
  def viewTransitions = {
    var s = toString + ".transitions = "
    transitions.foreach(s += _.toString)
    Log.info(s)
  }
  def totalWeight(trans: List[Transition]) = {
    var w = 0.0
    for (t <- trans) {
      w = w + t.action.weight
    }
    w
  }
  def timeout: Option[Transition] = {
    val tr = transitions.filter(!_.waitTime.isEmpty)
    if(tr.isEmpty) None else Some(tr.head)
  }

  def reduceInstances(n: Int) = {
    instanceNum -= n
    assert (instanceNum >= 0)
  }

  //Assign instances. If no transition is available and timeout is setted, register instances to scheduler.
  def assignInstances(n: Int) = {
    instanceNum += n
    Log.info(instanceNum + " instances are in state " + this.toString + ".")
    var remain = n
    if(!availableTransitions.isEmpty) {
      var s = "availableTransitions: "
      for(tr <- availableTransitions) {
        s = s + tr.toString+","
      }
      val totalW = totalWeight(availableTransitions)
      Log.debug(s+" totalW = " + totalW)
      val rnd = scala.util.Random.shuffle(availableTransitions)
      for(t <- rnd) {
        val tN = (n * t.action.weight / totalW).toInt
        if(tN > 0) {
          feasibleInstances = (t, tN) :: feasibleInstances
          Log.debug("added "+t.toString +" (" + tN +" instances) to feasibleInstances")
          remain = remain - tN
        }
      }
      feasibleInstances = (rnd.head, remain) :: feasibleInstances
      Log.debug("added "+rnd.head.toString +" (" + remain +" instances) to feasibleInstances")

    } else {
      timeout match {
        case Some(t) =>
          assignTimeout(t,n)
        case None => 
    }
  }

  def assignTimeout(t:Transition, n: Int) {
    t.action.waitTime match {
      case Some((x, y)) => 
        if(x == y) {
          registerToScheduler(t, x, n)
        } else {
          val width = (y - x) / (timeSlice - 1)
          val dividedN = (n / timeSlice).toInt
          for(i <- 0 to timeSlice - 1) {
            val remain = if(i < n - dividedN * timeSlice) 1 else 0
            registerToScheduler(t, x + (i * width).toInt, dividedN + remain)
          }
        }
      case None =>
      }
    }
  }

  def registerToScheduler(t:Transition, time: Int, n: Int) {
    val task = new TimeoutTask(t, n, getId)
    MBT.time.scheduler.scheduleOnce(time.millis)(task.run())
  }

  class TimeoutTask(t: Transition, n: Int, id: Int) extends Thread {
//concurrencyの問題を考慮する必要がありそう
    override def run() {
      if(!disabled(id)) {
        feasibleInstances = (t, n) :: feasibleInstances
      }
      waitingInstances -= id
    }
  }
}
