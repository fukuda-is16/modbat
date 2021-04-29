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
                        //println("while start")
                        // 仮想時間を先に進めて良いかどうか判定し，良ければ進める
                        if (enableAccelerate) {
                            //realtime_tokens から もう生きていないスレッドを削除
                            realTimeTokens = realTimeTokens.filter(p => p._2 match {
                                case Some(th) => th.getState != Thread.State.TERMINATED
                                case _ => true
                            })
                            //println("check real time token")
                            if (realTimeTokens.isEmpty) {
                                //println("no real time token")
                                if (taskQueue.nonEmpty) {
                                    val t = taskQueue.head.time
                                    virtRealDiff += (t - getCurrentVirtualTime()) max 0
                                }
                            }
                            //println("enableaccelerate block end")
                        }
                        
                        // 先頭のタスクの実行時刻が来ていたら，その時刻のタスクを全部実行
                        //     して，タスクから削除．
                        // 次のタスクの実行時刻までの実時間のtimeoutでwaitする．
                        if (taskQueue.isEmpty) { /*println("task queue is empty");*/ localLock.wait() }
                        else {
                            //println("task queue is not empty")
                            val Task(t0, _, task, optToken) = taskQueue.head
                            val waitTime = t0 - getCurrentVirtualTime()
                            //println(s"wait time is ${waitTime}")
                            if (waitTime > 0) { /*println(s"${waitTime} wait start");*/ localLock.wait(waitTime); /*println(s"${waitTime} wait end")*/ }
                            else {
                                //println("exec tasks")
                                var t1: Long = 0
                                breakable {
                                    while (taskQueue.nonEmpty) {
                                        //println(taskQueue)
                                        val Task(t_tmp, _, task, optToken) = taskQueue.head
                                        t1 = t_tmp
                                        if (t1 > t0) { break }
                                        taskQueue.dequeue()
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
                        //println(s"taskQueue: ${taskQueue}")
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
            if (waitingThreads contains lock) for(wt <- waitingThreads(lock)) {
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
            if (taskQueue.isEmpty && realTimeTokens.isEmpty) {
                localLock.notifyAll()
                true
            } else false
        }
    }
}