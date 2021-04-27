package accsched

object ASThread {
    //* as_wait(lock)
    def asWait(lock: AnyRef) = {
        //AccSched.waiting_threads[lock] に，this を追加する．
        AccSched.waitingThreads(lock) += Thread.currentThread.asInstanceOf[ASThread]
        //AccSched.cancel_disable_skip(token)
        AccSched.cancelDisableSkip(Thread.currentThread.asInstanceOf[ASThread].token)
        lock.synchronized { lock.wait() }
    }

    //* as_wait(lock, timeout, real)
    def asWait(lock: AnyRef, timeout: Long, real: Boolean) = {
        // taskID := AccSched.schedule(lambda{AccSched.as_notifyAll(lock);},
        //                             AccSched.get_current_virutal_time() + timeout,
        //                            real)
        val taskID = AccSched.schedule(
            AccSched.asNotifyAll(lock),
            timeout,
            real)
        //as_wait(lock)
        lock.synchronized {lock.wait()}
        AccSched.cancelSchedule(taskID)
    }

    //* sleep(time, real)
    def sleep(time: Long, real: Boolean = false) = {
        asWait(new AnyRef(), time, real)
    }
}


class ASThread extends Thread {

    //* object fields

    var token: Int = -1

    //* start()

    override def start() = {
        token = AccSched.disableSkip(Some(this))
        super.start()
    }
}