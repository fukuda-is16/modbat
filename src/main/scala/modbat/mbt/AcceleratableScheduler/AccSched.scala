package accsched

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import util.control.Breaks.{breakable, break}
import ASLog.debug

object AccSched  {

  val localLock = new AnyRef
  var enableAccelerate: Boolean = true
  var selfEnd: Boolean = true
  @volatile var virtRealDiff: Long = 0
  var curTaskID: Int = 0
  var taskQueue = collection.mutable.PriorityQueue[Task]()
  var realtimeReqs = new RealtimeReqs()
  var modified: Boolean = true
  var finished: Boolean = false
  @volatile var started: Boolean = false
  @volatile var forceTerminate: Boolean = false
  var ownerToken: Int = -1
  var runningTasks: Int = 0

  var schThread: Option[SchedulerThread] = None

  class SchedulerThread extends Thread {
    override def run(): Unit = {
      try {
        localLock.synchronized {
          while (!forceTerminate) {
            debug("SchedulerThread: top")
            // 仮想時間を先に進めて良いかどうか判定し，良ければ進める
            if (enableAccelerate) {
              /*  Removing dead threads from realTimeTokens ... obsolete
              debug("SchedulerThread: checking if we can advance VT");
              realTimeTokens = realTimeTokens.filter(p => p._2 match {
              case Some(th) => th.getState != Thread.State.TERMINATED
              case _ => true
              })
              */
              debug(s"realtimeReqs = ${realtimeReqs}")
              if (!modified && runningTasks == 0 && ! realtimeReqs.someEnabled) {
                if (taskQueue.nonEmpty) {
                  val t = taskQueue.head.getTime()
                  virtRealDiff += (t - getCurrentVirtualTime()) max 0
                  modified = true
                  debug(s"SchedulerThread: Advanced Virtual Time: virtRealDiff=${virtRealDiff}")
                }
              }
            }
            
            // 実行時刻が来ているタスクを全部実行して，タスクから削除．
            // 次のタスクの実行時刻までの実時間のtimeoutでwaitする．
            var waitTime: Long = -1
            val cvt: Long = getCurrentVirtualTime()
            breakable {
              while (! taskQueue.isEmpty) {
                val task = taskQueue.head
              // for ( Task(t0, _, task, optToken) <- taskQueue ) {
                if (task.getTime() > cvt) {
                  waitTime = task.getTime() - cvt
                  break
                }
                debug(s"SchedulerThread: executing a task")
                taskQueue.dequeue()
                task.getOptToken() match {
                  case Some(token) => { discardToken(token) }
                  case _ =>
                }
                debug(s"SchedulerThread: just before task.execute()")
                runningTasks += 1
                debug(s"runningTasks = ${runningTasks}")
                Future {
                  debug(s"in future")
                  task.execute()
                  localLock.synchronized {
                    modified = true
                    runningTasks -= 1
                    localLock.notifyAll()
                  }
                }
                debug(s"SchedulerThread: just after task.execute()")
              }
            }

            if (modified) {
              debug(s"SchedulerThread: notifying the owner.")
              localLock.notifyAll()
            }
            if (selfEnd) {
              finished = !modified &&
                        taskQueue.isEmpty &&
                        !realtimeReqs.someEnabled &&
                        runningTasks == 0
              if (finished) {
                debug(s"SchedulerThread is terminating.")
                localLock.notifyAll()
                return
              }
            }
            if (waitTime < 0) {
              debug(s"SchedulerThread is waiting indefinitely")
              localLock.wait()
            }else {
              debug(s"SchedulerThread is waiting for ${waitTime} in RealTime")
              localLock.wait(waitTime)
            }
            debug(s"SchedulerThread wakes up.")
          }
          debug("SchedulerThread is terminating due to forceTerminate")
          localLock.notifyAll()
          finished = true
        }
      } catch {
        case e: InterruptedException =>
      }
    }
  }

  def init(en_acc: Boolean = true, self_end: Boolean = true): Unit = {
    localLock.synchronized {
      for(th <- ASThread.allThreads) {
        th.interrupt()
      }
      schThread match {
        case Some(th) => th.interrupt()
        case None => 
      }
    }

    started = true
    forceTerminate = false
    finished = false
    enableAccelerate = en_acc
    selfEnd = self_end
    virtRealDiff = 0
    curTaskID = 0
    taskQueue = collection.mutable.PriorityQueue[Task]()
    realtimeReqs = new RealtimeReqs()
    modified = false
    ownerToken = getToken()
    
    val th = new SchedulerThread()
    schThread = Some(th)
    th.start()

    ASThread.allThreads = scala.collection.mutable.ArrayBuffer[ASThread]()
  }

  def schedule(action: => Unit, timeout: Long, real: Boolean = false): Int = {
    localLock.synchronized {
      runcheck()
      val time: Long = getCurrentVirtualTime + timeout
      val optToken = if (real) Some(getToken()) else None
      taskQueue += new Task(time, curTaskID, action, optToken)
      localLock.notifyAll();
      debug(s"AccSched::schedule(): added: taskQueue = ${taskQueue}")
      curTaskID += 1
      curTaskID - 1
    }
  }

  def cancelSchedule(taskID: Int): Unit = {
    localLock.synchronized {
      runcheck()
      // FIXME: this is too inefficient.
      //   Possible improvement: add "enable" filed in Task.
      //   In the Task object, make "instances" map from IDs to Task objects.
      //   taskQueue objects should also reside in the Task object.
      val oldTaskQueue = taskQueue
      taskQueue = collection.mutable.PriorityQueue[Task]()
      for ( elem <- oldTaskQueue ) {
        if (elem.getTaskID() == taskID) {
          elem.getOptToken() match {
            case Some(token) => discardToken(token)
            case _ =>
          }
        }else {
          taskQueue += elem
        }
      }
      localLock.notifyAll();
      debug(s"AccSched::cancelSchedule(): removed: taskQueue = ${taskQueue}")
    }
  }

  def getToken(initVal: Boolean = true): Int = {
    localLock.synchronized {
      runcheck()
      realtimeReqs.newToken(initVal)
    }
  }

  def discardToken(token: Int): Unit = {
    localLock.synchronized {
      runcheck()
      realtimeReqs.discard(token)
      localLock.notifyAll()
    }
  }

  def askRealtime(token: Int): Unit = {
    localLock.synchronized {
      runcheck()
      realtimeReqs.setValue(token, true)
      debug(s"Ask realtime ${token}: realtimeReqs = ${realtimeReqs}")
      localLock.notifyAll()
    }
  }

  def cancelRealtime(token: Int): Unit = {
    localLock.synchronized {
      runcheck()
      realtimeReqs.setValue(token, false)
      debug(s"Cancel realtime ${token}: realtimeReqs = ${realtimeReqs}")
      localLock.notifyAll()
    }
  }

  // FIXME: The current implementation has a bug in that when a timeout comes to a thread,
  //    then all other threads waiting on the same objects will be notified and waken up.
  //    Possible fix could be that waitingThreads have information for timeout expiration time
  //    and when notified by the task associated with asWaitBase, the stored information
  //    is to be checked with the current time.
  def asWaitBase(lock: AnyRef, timeout: Long, real: Boolean, oth: Option[ASThread]): Unit = {
    runcheck()
    debug(s"AccSched::asWaitBase is called.  lock=${lock}, timeout=${timeout}, real=${real}, oth=${oth}");
    var taskID = -1;
    localLock.synchronized {
      if (timeout >= 0) {
        taskID = schedule({
          debug(s"lock ($lock) schedule requesting");
          lock.synchronized{
            debug(s"lock ($lock) schedule got");
            AccSched.asNotifyAll(lock)
          }
          debug(s"lock ($lock) schedule released");
        }, timeout, real);
      }
      oth match {
        case Some(th) => {
          ASThread.waitingThreads += th -> lock
          cancelRealtime(th.token)
        }
        case _ =>
      }
    }
    // FIXME: Check if it is OK when timeout is small ... 0 or 1 or something
    //    Note that you should not call AnyRef::wait(timeout)
    //    because you need to maintain ASThread.waitingThreads
    debug("asWaitBase.  about to wait");
    lock.wait()
    debug("asWaitBase.  resumed from wait");
    if (taskID >= 0) {
      AccSched.cancelSchedule(taskID)
    }
    debug(s"AccSched::asWaitBase is finished.  lock=${lock}, timeout=${timeout}, real=${real}, oth=${oth}");
  }

  def asWait(lock: AnyRef): Unit = { asWaitBase(lock, -1, false, None) }

  def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = { asWaitBase(lock, timeout, real, None) }

  def asNotifyAll(lock: AnyRef): Unit = {
    debug(s"asNotifyAll is called for ${lock}")
    localLock.synchronized {
      debug(s"AccSched::asNotifyAll.  before: waitingThreads=${ASThread.waitingThreads}")
      for ((th, lk) <- ASThread.waitingThreads)
        if (lock eq lk) { // the same object
          debug(s"asNotifyAll: realTime is being asked for ${th}")
          askRealtime(th.token)
          ASThread.waitingThreads -= th
        }
      lock.synchronized {
        lock.notifyAll()
      }
    }
  }

  def taskWait(): Boolean = {
    localLock.synchronized {
      if (finished) {
        debug("taskWait: returning false immediately")
        return false
      }
      if (modified) {
        debug("taskWait: modified.  Immediate Returning");
      }else {
        debug("taskWait: waiting")
        cancelRealtime(ownerToken)
        localLock.notifyAll()
        localLock.wait()
        if (finished) {
          debug("taskWait: returning false after waking up")
          return false
        }
        askRealtime(ownerToken)
        debug(s"taskWait: returning true")
      }
      modified = false;
      return true
    }
  }

  def taskNotify(): Unit = {
    localLock.synchronized {
      debug("taskNotify is running")
      askRealtime(ownerToken)
      modified = true;
      localLock.notifyAll();
    }
  }


  def getCurrentVirtualTime(): Long = {
    System.currentTimeMillis() + virtRealDiff
  }

  def shutdown(): Unit = {
    localLock.synchronized {
      forceTerminate = true
      localLock.notifyAll()
    }
  }

  def runcheck(): Unit = {
    if (!started) {
      throw new RuntimeException("Scheduler Thread is not running.  Please check if init() has been called.")
    }
  }

}
