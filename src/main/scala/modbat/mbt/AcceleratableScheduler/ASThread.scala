package accsched

object ASThread {

    def curTh: ASThread = { Thread.currentThread.asInstanceOf[ASThread] }

    def sleep(time: Long, real: Boolean = false) = {
	println(s"ASThread::sleep is called at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
        val lock = new AnyRef()
        lock.synchronized { AccSched.asWaitBase(lock, time, real, Some(curTh)); }
	println(s"ASThread::sleep is finished at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
    }

    def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = {
        AccSched.asWaitBase(lock, timeout, real, Some(curTh))
    }

    def asWait(lock: AnyRef): Unit = {
        AccSched.asWaitBase(lock, -1, false, Some(curTh))
    }

}



abstract class ASThread extends Thread {

    val token: Int = AccSched.getToken()

    // FIXME: auxiliary constructor that takes a Runnable

    def run_body(): Unit

    override def run(): Unit = {
        AccSched.askRealtime(token)
        println("ASThread: calling run_body()")
        run_body()
        println("ASThread: returning from run_body()")
        AccSched.discardToken(token)
    }

}
