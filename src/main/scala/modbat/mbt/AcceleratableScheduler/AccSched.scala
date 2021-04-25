package accsched

import util.control.Breaks.{breakable, break}

object AccSched {
    var enableAccelerate = true;

    val localLock = new AnyRef
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

                        // 仮想時間を先に進めて良いかどうか判定し，良ければ進める
                        if (enableAccelerate) {
                            //realtime_tokens から もう生きていないスレッドを削除
                            realTimeTokens = realTimeTokens.filter(p => p._2 match {
                                case Some(th) => th.getState != Thread.State.TERMINATED
                                case _ => true
                            })
                            if (realTimeTokens.isEmpty) {
                                if (taskQueue.nonEmpty) {
                                    val t = taskQueue.head.time
                                    virtRealDiff += (t - getCurrentVirtualTime()) max 0
                                }
                            }
                        }
                        
                        // 先頭のタスクの実行時刻が来ていたら，その時刻のタスクを全部実行
                        //     して，タスクから削除．
                        // 次のタスクの実行時刻までの実時間のtimeoutでwaitする．
                        if (taskQueue.isEmpty) { localLock.wait() }
                        else {
                            val Task(t0, _, task, optToken) = taskQueue.head
                            val waitTime = t0 - getCurrentVirtualTime()
                            if (waitTime > 0) { localLock.wait(waitTime) }
                            else {
                                var t1: Long = 0
                                breakable {
                                    while (taskQueue.nonEmpty) {
                                        val Task(t_tmp, _, task, optToken) = taskQueue.dequeue()
                                        t1 = t_tmp
                                        if (t1 > t0) { break }
                                        optToken match {
                                            case Some(token) => { cancelDisableSkip(token) }
                                            case _ =>
                                        }
                                        task.run()
                                        //AccSched.notifyAll()
                                        AccSched.synchronized {
                                            AccSched.notifyAll()
                                        }
                                    }
                                }
                                if (taskQueue.isEmpty) { localLock.wait() }
                                else { localLock.wait(t1 - getCurrentVirtualTime()) }
                            }
                        }
                        val isFinished = finished()
                        println(isFinished)
                        if (isFinished) {
                            println("end of main loop")
                            return
                        }
                    }
                }
            }
        }
        th.start()
    }

    def schedulerNotify(): Unit = {
        localLock.synchronized {
            localLock.notifyAll()
        }
    }

    private var taskID: Int = 0
    // returns task ID
    def schedule(task: => Unit, timeout: Long, real: Boolean = false): Int = {
        localLock.synchronized {
            val time: Long = getCurrentVirtualTime + timeout
            val optToken = if (real) Some(disableSkip(None)) else None
            taskQueue += new Task(time, curTaskID, new Runnable{override def run = task}, optToken)
            taskID += 1
            taskID
        }
    }

    def cancelSchedule(taskID: Int): Unit = {
        localLock.synchronized {
            taskQueue = taskQueue.filter{(t: Task) => t.taskID != taskID}
            val tmp = scala.collection.mutable.ArrayBuffer[Task]()
            while(taskQueue.nonEmpty) {
                val t: Task = taskQueue.dequeue()
                if (t.taskID != taskID) {
                    tmp += t
                }
            }
            taskQueue ++= tmp
        }
    }

    def disableSkip(th: Option[ASThread]): Int = {
        localLock.synchronized {
            realTimeTokens += curToken -> th
            val ret = curToken
            curToken += 1
            ret
        }
    }

    def cancelDisableSkip(token: Int): Unit = {
        localLock.synchronized {
            realTimeTokens = realTimeTokens.filter(p => p._1 != token)
            localLock.synchronized {
                localLock.notifyAll()
            }
        }
    }

    def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = {
        val tid = schedule({
            lock.synchronized {
                lock.notifyAll()
            }}, timeout, real)
        lock.synchronized {
            lock.wait()
        }
        cancelSchedule(tid)
    }

    def asNotifyAll(lock: AnyRef): Unit = {
        localLock.synchronized {
            for(wt <- waitingThreads(lock)) {
                wt.token = disableSkip(Some(wt))
            }
            lock.synchronized {
                lock.notifyAll()
            }
        }
    }

    def taskWait(): Unit = {
        localLock.synchronized {
            localLock.notifyAll()
        }
        AccSched.synchronized {
            AccSched.wait()
        }
    }

    def getCurrentVirtualTime(): Long = {
        localLock.synchronized {
            System.currentTimeMillis() + virtRealDiff
        }
    }

    def finished(): Boolean = {
        localLock.synchronized {
            println("check finished")
            println(taskQueue)
            println(realTimeTokens)
            println(taskQueue.isEmpty && realTimeTokens.isEmpty)
            taskQueue.isEmpty && realTimeTokens.isEmpty
        }
    }
}