package accsched

import util.control.Breaks.{breakable, break}

object AccSched {
    var enableAccelerate = true;

    // val localLock = new AnyRef
    val localLock = AccSched
    var virtRealDiff: Long = 0
    var curToken: Int = 0
    var curTaskID: Int = 0
    var taskQueue = collection.mutable.PriorityQueue[Task]()
    var realTimeTokens = scala.collection.mutable.ListBuffer[(Int, Option[ASThread])]()
    val waitingThreads = scala.collection.mutable.Map[AnyRef, scala.collection.mutable.ListBuffer[ASThread]]()

    def init(ea: Boolean = true): Unit = {
        enableAccelerate = ea

        val th = new Thread {
            override def run(): Unit = {
                localLock.synchronized {
                    while (true) {
                        println("SchedulerThread: top")
                        // 仮想時間を先に進めて良いかどうか判定し，良ければ進める
                        if (enableAccelerate) {
                            //realtime_tokens から もう生きていないスレッドを削除
                            println("SchedulerThread: checking if we can advance VT");
                            realTimeTokens = realTimeTokens.filter(p => p._2 match {
                                case Some(th) => th.getState != Thread.State.TERMINATED
                                case _ => true
                            })
                            println(s"realTimeTokens = ${realTimeTokens}")
                            if (realTimeTokens.isEmpty) {
                                if (taskQueue.nonEmpty) {
                                    val t = taskQueue.head.time
                                    virtRealDiff += (t - getCurrentVirtualTime()) max 0
                                    println(s"Advanced Virtual Time: virtRealDiff=${virtRealDiff}, vt=${getCurrentVirtualTime()}");
                                }
                            }
                        }
                        
                        // 先頭のタスクの実行時刻が来ていたら，その時刻のタスクを全部実行
                        //     して，タスクから削除．
                        // 次のタスクの実行時刻までの実時間のtimeoutでwaitする．
                        if (taskQueue.isEmpty) {
                            println("SchedulerThread is waiting indefinitely.")
                            localLock.wait()
                            println(s"SchedulerThread wakes up.")
                        }
                        else {
                            val Task(t0, _, task, optToken) = taskQueue.head
                            val waitTime = t0 - getCurrentVirtualTime()
                            if (waitTime > 0) {
                                println(s"SchedulerThread is waiting for ${waitTime} in RealTime");
                                localLock.wait(waitTime);
                                println(s"SchedulerThread wakes up.")
                            }
                            else {
                                println(s"SchedulerThread: executing tasks")
                                var t1: Long = 0
                                breakable {
                                    while (taskQueue.nonEmpty) {
                                        println(taskQueue)
                                        val Task(t_tmp, _, task, optToken) = taskQueue.head
                                        t1 = t_tmp
                                        if (t1 > t0) { break }
                                        taskQueue.dequeue()
                                        optToken match {
                                            case Some(token) => { cancelDisableSkip(token) }
                                            case _ =>
                                        }
                                        task.run()
                                        AccSched.synchronized {
                                            println(s"SchedulerThread: sending notification to AccSched")
                                            AccSched.notifyAll()
                                        }
                                    }
                                }
                                if (taskQueue.isEmpty) {
                                    println(s"SchedulerThread is waiting after executing tasks indefinitely")
                                    localLock.wait()
                                    println(s"SchedulerThread wakes up.")
                                }
                                else {
                                    var t2: Long = t1 - getCurrentVirtualTime()
                                    println(s"SchedulerThread is waiting for ${t2} in RealTime after executing tasks.")
                                    localLock.wait(t2)
                                    println(s"SchedulerThread wakes up.")
                                }
                            }
                        }
                        val isFinished = finished()
                        if (isFinished) {
                            return
                        }
                    }
                }
            }
        }
        th.start()
    }
    /*
     def schedulerNotify(): Unit = {
     localLock.synchronized {
     localLock.notifyAll()
     }
     }
     */
    //private var taskID: Int = 0
    // returns task ID
    def schedule(task: => Unit, timeout: Long, real: Boolean = false): Int = {
        localLock.synchronized {
            localLock.notifyAll();
            val time: Long = getCurrentVirtualTime + timeout
            val optToken = if (real) Some(disableSkip(None)) else None
            taskQueue += new Task(time, curTaskID, new Runnable{override def run = task}, optToken)
            println(s"added: taskQueue = ${taskQueue}")
            curTaskID += 1
            curTaskID - 1
        }
    }

    def cancelSchedule(taskID: Int): Unit = {
        //println(s"cancel task ${taskID}")
        //println(s"before queue ${taskQueue}")
        localLock.synchronized {
            localLock.notifyAll();
            taskQueue = taskQueue.filter{(t: Task) => t.taskID != taskID}
            println(s"removed: taskQueue = ${taskQueue}")
            /*val tmp = scala.collection.mutable.ArrayBuffer[Task]()
             while(taskQueue.nonEmpty) {
             val t: Task = taskQueue.dequeue()
             if (t.taskID != taskID) {
             tmp += t
             }
             }
             taskQueue ++= tmp
             */
        }
        //println(s"after queue ${taskQueue}")
    }

    def disableSkip(th: Option[ASThread]): Int = {
        localLock.synchronized {
            realTimeTokens += curToken -> th
            println(s"added ${curToken}: realTimeTokens = ${realTimeTokens}")
            val ret = curToken
            curToken += 1
            ret
        }
    }

    def cancelDisableSkip(token: Int): Unit = {
        localLock.synchronized {
            realTimeTokens = realTimeTokens.filter(p => p._1 != token)
            println(s"removed ${token}: realTimeTokens = ${realTimeTokens}")
            localLock.synchronized {
                localLock.notifyAll()
            }
        }
    }

    def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = {
        println(s"AccSched::asWait is called for ${lock}");
        val tid = schedule({
            lock.synchronized {
                lock.notifyAll()
            }}, timeout, real)
        lock.synchronized {
            lock.wait()
        }
        cancelSchedule(tid)
        println(s"Returning from AccSched::asWait for ${lock}")
    }

    def asNotifyAll(lock: AnyRef): Unit = {
        println(s"asNotifyAll is called for ${lock}")
        localLock.synchronized {
            if (waitingThreads contains lock) {
                for (wt <- waitingThreads(lock)) {
                    wt.token = disableSkip(Some(wt))
                }
                waitingThreads -= lock
            }
            lock.synchronized {
                lock.notifyAll()
            }
        }
    }

    def taskWait(): Unit = {
        println("Entering taskWait");
        localLock.synchronized {
            localLock.notifyAll()
            println("taskWait() has issued notifyAll");
        }
        AccSched.synchronized {
            AccSched.wait()
        }
        println("Returning from taskWait()");
    }

    def getCurrentVirtualTime(): Long = {
        localLock.synchronized {
            System.currentTimeMillis() + virtRealDiff
        }
    }

    def finished(): Boolean = {
        localLock.synchronized {
            if (taskQueue.isEmpty && realTimeTokens.isEmpty) {
                localLock.notifyAll()
                true
            } else false
        }
    }
}
