package modbat.dsl

import modbat.cov.StateCoverage
import modbat.log.Log
import modbat.mbt.{MBT, MessageHandler}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import accsched._

class State (val name: String) {

  class FeasibleInstances {
      val lock = new AnyRef
      var assignedInstances = scala.collection.mutable.Map[Transition, Int]()

      def isEmpty: Boolean = {
          lock.synchronized {
              return assignedInstances.isEmpty
          }
      }
      def add(t: Transition, n: Int): Unit = {
        if (n == 0) {
          Log.debug(s"(state $toString) no instance to add")
          return
        }
        lock.synchronized {
          assignedInstances(t) = assignedInstances.getOrElse(t, 0) + n
          Log.debug(s"(state $toString) added ${t.toString} ($n  instances) to feasibleInstances")
        }
      }
      def reset(): scala.collection.mutable.Map[Transition, Int] = {
          lock.synchronized {
              val ret = assignedInstances
              assignedInstances = scala.collection.mutable.Map[Transition, Int]()
              return ret
          }
      }
  }




  override def toString = name
  var coverage: StateCoverage = _

  var instanceNum = 0
  val feasibleInstances = new FeasibleInstances
  def feasible = instanceNum > 0 && !feasibleInstances.isEmpty

  //@volatile var waitingInstances: Map[Int, Int] = Map.empty//key: id, value: (instanceNum,disabled)
  val timeoutTaskIDs = scala.collection.mutable.Map[Int, Int]() // holds 'taskID -> instanceNum' currently registered in task queue

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
  // def addFeasibleInstances(t: Transition, n: Int) = {
  //   if(n > 0) {
  //     if(feasibleInstances.contains(t)) {
  //       feasibleInstances = feasibleInstances.updated(t, feasibleInstances(t) + n)
  //     } else {
  //       feasibleInstances = feasibleInstances.updated(t, n)
  //     }
  //     Log.debug(s"(state $toString) added ${t.toString} ($n  instances) to feasibleInstances")
  //   }
  //   else Log.debug(s"(state $toString) no instance to add")
  // }
  // def cancelFeasibleInstances = {
  //   feasibleInstances = Map.empty
  // }


  // returns num of instances previously assigned to timeout transition
  def disableTimeout(): Int = {
    var total = 0
    timeoutTaskIDs.synchronized {
      for((taskid, num) <- timeoutTaskIDs) {
        // cancelation may fail because of race condition
        val cancelSuccess = AccSched.cancelSchedule(taskid)
        if (cancelSuccess) {
          total += num
        }
      }
      timeoutTaskIDs.clear()
    }
    return total
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
    if(instanceNum < 0) {
      val prevNum = instanceNum + n
      Log.debug(s"(state $toString) number of instances is negative: ${prevNum}, reduce: ${n}")
      Log.info(s"(state $toString) number of instances is negative: ${prevNum}, reduce: ${n}")
      Log.error(s"(state $toString) number of instances is negative")
    }
  }

  //Assign instances. If no transition is available and timeout is setted, register instances to scheduler.
  def assignInstances(n: Int): Unit = {
    Log.debug(s"$instanceNum instance(s) are in state ${this.toString}.")
    if(!availableTransitions.isEmpty) {
      // instanceNum will be incremented in this method
      assignFreeInstToTrans(n)
    } else {
      instanceNum += n
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
  def assignFreeInstToTrans(n: Int): Unit = {
    // var s = "(state " + this.toString + ") availableTransitions: "
    // for(tr <- availableTransitions) {
    //   s = s + tr.toString+","
    // }


    // currently available transitions excluding those whose predicate if defined is false
    val ts: List[Transition] = transitions.filter(
      t => t.action.guardFunc match {
        case Some(b) if (!b()) => false
        case _ => true
      }
    )
    val tNum = ts.size // # of available transitions
    if (tNum == 0) {
      // discard n instances since no transitions available
      return
    }
    // assign n instances to transitions
    instanceNum += n

    // in the following, distributes instances to available transitions according to their weight
    var totalWeight: Double = 0.0
    for(t <- ts) totalWeight += t.action.weight
    // scale up totalWeight s.t. scaled_totalWeight = scale * original_totalWeight = n
    var scale = n.toDouble / totalWeight

    // to each transition with (scaled) weight w, assign floor(w) instances
    var rest = n // # of not-assigned instances
    val instanceDistribution = Array.fill(tNum)(0) // # of instances to assign later for each transition
    for((t, idx) <- ts.zipWithIndex) {
      val scaledWeight = scale * t.action.weight
      val num = scaledWeight.toInt
      instanceDistribution(idx) += num
      rest -= num
    }
    assert(0 <= rest && rest <= tNum)
    
    // for each not-assigned instance, decide their next transition randomly one by one
    for(_ <- 1 to rest) {
      val r = MBT.rng.nextDouble() // (0, 1)
      val rw = r * totalWeight
      assert(0.0 <= r && r <= 1.0)
      // linearly search for the choice
      import scala.util.control.Breaks.{breakable, break}
      breakable {
        var acc: Double = 0.0
        for((t, idx) <- ts.zipWithIndex) {
          acc += t.action.weight
          if (idx + 1 == tNum || acc >= rw) {
            // choose here
            instanceDistribution(idx) += 1
            break
          }
        }
        // has to have break-ed in for-loop
        assert(false)
      }
    }

    // finally, assign instances to each transition according to instanceDistribution
    for((t, idx) <- ts.zipWithIndex) {
      val num = instanceDistribution(idx)
      if (num > 0) {
        Log.info(s"$num instances newly assigned to transition from ${t.origin.name} to ${t.dest.name}")
        feasibleInstances.add(t, num)
      }
    }

    // val totalW = totalWeight(availableTransitions)
    // val rnd = scala.util.Random.shuffle(availableTransitions)
    // for(t <- rnd) {
    //   val tN = (n * t.action.weight / totalW).toInt
    //   if(tN > 0) {
    //     addFeasibleInstances(t, tN)
    //     remain = remain - tN
    //   }
    // }
    // addFeasibleInstances(rnd.head, remain)
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
        feasibleInstances.add(t, n)
        return
      }
      val task = new TimeoutTask(t, n)
      Log.debug(s"registered task to execute ${t.toString} for $n instances in $time millis")
      timeoutTaskIDs.synchronized {
        val taskid = AccSched.schedule({
          task.execute()
        }, time, real)
        task.taskid = taskid
        timeoutTaskIDs(taskid) = n
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
      feasibleInstances.add(t, n)

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
    // feasibleInstances.reset()
    val num = disableTimeout()
    feasibleInstances.add(trans, num)
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
