package accsched

import util.control.Breaks.{breakable, break}

object AccSched  {

    val localLock = new AnyRef
    val waitingThreads = scala.collection.mutable.Map[AnyRef, scala.collection.mutable.ListBuffer[ASThread]]()
    var enableAccelerate: Boolean = true
    var selfEnd: Boolean = true
    var virtRealDiff: Long = 0
    var curTaskID: Int = 0
    var taskQueue = collection.mutable.PriorityQueue[Task]()
    var realtimeReqs = new RealtimeReqs()
    var modified: Boolean = true
    var finished: Boolean = false
    var forceTerminate: Boolean = false
    var ownerToken: Int = getToken(true)

    class SchedulerThread extends Thread {
        override def run(): Unit = {
            localLock.synchronized {
                while (!forceTerminate) {
                    println("SchedulerThread: top")
                    // 仮想時間を先に進めて良いかどうか判定し，良ければ進める
                    if (enableAccelerate) {
                        /*  Removing dead threads from realTimeTokens ... obsolete
                         println("SchedulerThread: checking if we can advance VT");
                         realTimeTokens = realTimeTokens.filter(p => p._2 match {
                         case Some(th) => th.getState != Thread.State.TERMINATED
                         case _ => true
                         })
                         */
                        println(s"realtimeReqs = ${realtimeReqs}")
                        if (! realtimeReqs.someEnabled) {
                            if (taskQueue.nonEmpty) {
                                val t = taskQueue.head.time
                                virtRealDiff += (t - getCurrentVirtualTime()) max 0
                                modified = true
                                println(s"SchedulerThread: Advanced Virtual Time: virtRealDiff=${virtRealDiff}, vt=${getCurrentVirtualTime()}");
                            }
                        }
                    }
                    
                    // 実行時刻が来ているタスクを全部実行して，タスクから削除．
                    // 次のタスクの実行時刻までの実時間のtimeoutでwaitする．
                    var waitTime: Long = -1
                    val cvt: Long = getCurrentVirtualTime()
                    breakable {
                        for ( Task(t0, _, task, optToken) <- taskQueue ) {
                            if (t0 > cvt) {
                                waitTime = t0 - cvt
                                break
                            }
                            modified = true
                            println(s"SchedulerThread: executing a task")
                            taskQueue.dequeue()
                            optToken match {
                                case Some(token) => { discardToken(token) }
                                case _ =>
                            }
                            task.run()
                        }
                    }

                    if (modified) {
                        println(s"SchedulerThread: notifying the owner.")
                        localLock.notifyAll()
                    }
                    if (selfEnd) {
                        finished = !modified && taskQueue.isEmpty && !realtimeReqs.someEnabled
                        if (finished) {
                            println(s"SchedulerThread is terminating.")
                            localLock.notifyAll()
                            return
                        }
                    }
                    if (waitTime < 0) {
                        println(s"SchedulerThread is waiting indefinitely")
                        localLock.wait()
                    }else {
                        println(s"SchedulerThread is waiting for ${waitTime} in RealTime")
                        localLock.wait(waitTime)
                    }
                    println(s"SchedulerThread wakes up.")
                }
                println("SchedulerThread is terminating due to forceTerminate")
                localLock.notifyAll()
                finished = true
            }
        }
    }

    def init(en_acc: Boolean = true, self_end: Boolean = true): Unit = {

        enableAccelerate = en_acc
        selfEnd = self_end
        virtRealDiff = 0
        curTaskID = 0
        taskQueue = collection.mutable.PriorityQueue[Task]()
        realtimeReqs = new RealtimeReqs()
        modified = false
        finished = false
        forceTerminate = false
        ownerToken = getToken(true)
        
        val th = new SchedulerThread()
        th.start()
    }

    def schedule(task: => Unit, timeout: Long, real: Boolean = false): Int = {
        localLock.synchronized {
            val time: Long = getCurrentVirtualTime + timeout
            val optToken = if (real) Some(getToken(true)) else None
            taskQueue += new Task(time, curTaskID, new Runnable{override def run = task}, optToken)
            localLock.notifyAll();
            println(s"AccSched::schedule(): added: taskQueue = ${taskQueue}")
            curTaskID += 1
            curTaskID - 1
        }
    }

    def cancelSchedule(taskID: Int): Unit = {
        localLock.synchronized {
            // FIXME: this is too inefficient.
            //   Possible improvement: add "eneble" filed in Task.
            //   In the Task object, make "instances" map from IDs to Task objects.
            //   taskQueue objects should also reside in the Task object.
            val oldTaskQueue = taskQueue
            taskQueue = collection.mutable.PriorityQueue[Task]()
            for ( elem <- oldTaskQueue ) {
                elem match {
                    case Task(_, id, _, ot) => {
                        if (id == taskID) {
                            ot match {
                                case Some(token) => discardToken(token)
                                case _ =>
                            }
                        }else {
                            taskQueue += elem
                        }
                    }
                }
            }
            localLock.notifyAll();
            println(s"AccSched::cancelSchedule(): removed: taskQueue = ${taskQueue}")
        }
    }

    def getToken(initVal: Boolean = false): Int = {
        localLock.synchronized {
            realtimeReqs.newToken(initVal)
        }
    }

    def discardToken(token: Int): Unit = {
        localLock.synchronized {
            realtimeReqs.discard(token)
            localLock.notifyAll()
        }
    }

    def askRealtime(token: Int): Unit = {
        localLock.synchronized {
            realtimeReqs.setValue(token, true)
            println(s"Ask realtime ${token}: realtimeReqs = ${realtimeReqs}")
            localLock.notifyAll()
        }
    }

    def cancelRealtime(token: Int): Unit = {
        localLock.synchronized {
            realtimeReqs.setValue(token, false)
            println(s"Cancel realtime ${token}: realtimeReqs = ${realtimeReqs}")
            localLock.notifyAll()
        }
    }

    // FIXME: The current implementation has a bug in that when a timeout comes to a thread,
    //    then all other threads waiting on the same objects will be notified and waken up.
    //    Possible fix could be that waitingThreads have information for timeout expiration time
    //    and when notified by the task associated with asWaitBase, the stored information
    //    is to be checked with the current time.
    def asWaitBase(lock: AnyRef, timeout: Long, real: Boolean, oth: Option[ASThread]): Unit = {
        println(s"AccSched::asWaitBase is called.  lock=${lock}, timeout=${timeout}, real=${real}, oth=${oth}");
        var taskID = -1;
        localLock.synchronized {
            if (timeout >= 0) {
                taskID = schedule({lock.synchronized{AccSched.asNotifyAll(lock)}}, timeout, real);
            }
            oth match {
                case Some(th) => {
                    if (! (AccSched.waitingThreads contains lock)) {
                        AccSched.waitingThreads(lock) = scala.collection.mutable.ListBuffer[ASThread]()
                    }
                    AccSched.waitingThreads(lock) += th
                    AccSched.cancelRealtime(th.token)
                }
                case _ =>
            }
        }
        /*
        if (timeout >= 0) {
            lock.wait(timeout)  // Just in case timeout == 0 and scheduled notifyAll has issued at once.
        }else {
         */
            // FIXME: Check if it is OK when timeout is small ... 0 or 1 or something
            lock.wait()
        /*
        }
         */
        if (taskID >= 0) {
            AccSched.cancelSchedule(taskID)
        }
        println(s"AccSched::asWaitBase is finished.  lock=${lock}, timeout=${timeout}, real=${real}, oth=${oth}");
    }

    def asWait(lock: AnyRef): Unit = { asWaitBase(lock, -1, false, None) }

    def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = { asWaitBase(lock, timeout, real, None) }

    def asNotifyAll(lock: AnyRef): Unit = {
        println(s"asNotifyAll is called for ${lock}")
        localLock.synchronized {
            if (waitingThreads contains lock) {
                for (wt <- waitingThreads(lock)) {
                    askRealtime(wt.token)
                }
                waitingThreads -= lock
            }
            lock.synchronized {
                lock.notifyAll()
            }
        }
    }

    def taskWait(): Boolean = {
        localLock.synchronized {
            if (finished) {
                println("taskWait: returning false immediately")
                return false
            }
            if (modified) {
                println("taskWait: modified.  Immediate Returning");
            }else {
                println("taskWait: waiting")
                cancelRealtime(ownerToken)
                localLock.notifyAll()
                localLock.wait()
                if (finished) {
                    println("taskWait: returning false after waking up")
                    return false
                }
                askRealtime(ownerToken)
                println(s"taskWait: returning true")
            }
            modified = false;
            return true
        }
    }

    def taskNotify(): Unit = {
        localLock.synchronized {
            modified = true;
            localLock.notifyAll();
        }
    }


    def getCurrentVirtualTime(): Long = {
        localLock.synchronized {
            System.currentTimeMillis() + virtRealDiff
        }
    }

    def shutdown(): Unit = {
        localLock.synchronized {
            forceTerminate = true
            localLock.notifyAll()
        }
    }

}
