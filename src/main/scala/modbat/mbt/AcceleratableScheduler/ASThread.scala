package accsched

object ASThread {
    //* as_wait(lock)
    def asWait(lock: AnyRef): Unit = {
        println(s"ASThread::asWait is called for ${lock}")
        //AccSched.waiting_threads[lock] に，this を追加する．
        if (! (AccSched.waitingThreads contains lock)) {
            AccSched.waitingThreads(lock) = scala.collection.mutable.ListBuffer[ASThread]()
        }
        AccSched.waitingThreads(lock) += Thread.currentThread.asInstanceOf[ASThread]
        //AccSched.cancel_disable_skip(token)
        AccSched.cancelDisableSkip(Thread.currentThread.asInstanceOf[ASThread].token)
        lock.wait()
        println(s"Returning from ASThread::asWait for ${lock}")
    }

    //* as_wait(lock, timeout, real)
    def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = {
        println(s"ASThread::asWait is called for ${lock} with timeout ${timeout}")
        val taskID = AccSched.schedule(
            AccSched.asNotifyAll(lock),
            timeout,
            real)
        asWait(lock);
        AccSched.cancelSchedule(taskID)
    }

    //* sleep(time, real)
    def sleep(time: Long, real: Boolean = false) = {
	println(s"ASThread::sleep is called at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
        val lock = new AnyRef()
        lock.synchronized { asWait(lock, time, real); }
	println(s"ASThread::sleep is finished at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
    }
}


class ASThread extends Thread {

    var token: Int = -1

    override def start() = {
        val token = AccSched.disableSkip(Some(this))
        super.start()
        AccSched.cancelDisableSkip(token)
    }
}
