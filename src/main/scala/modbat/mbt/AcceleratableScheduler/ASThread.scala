package accsched

object ASThread {

  def curTh: ASThread = { Thread.currentThread.asInstanceOf[ASThread] }

  def sleep(time: Long, real: Boolean = false) = {
    println(s"ASThread::sleep(${time}) is called at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
    val lock = new AnyRef()
    lock.synchronized { AccSched.asWaitBase(lock, time, real, Some(curTh)); }
    println(s"ASThread::sleep(${time}) is finished at ${AccSched.getCurrentVirtualTime()}, ${System.currentTimeMillis()}");
  }

  def asWait(lock: AnyRef, timeout: Long, real: Boolean = false): Unit = {
    AccSched.asWaitBase(lock, timeout, real, Some(curTh))
  }

  def asWait(lock: AnyRef): Unit = {
    AccSched.asWaitBase(lock, -1, false, Some(curTh))
  }

  /*
   In an old implementation, we used a map waitingThreads of
   type Map[AnyRef, ListBuffer[ASThread]] to maintain the
   list of threads wait()ing with the lock of an AnyRef.
   Unfortunately, this approach did not work because Map
   apparently uses obj.hashCode() of AnyRef obj for its key.
   Thus, if an AnyRef is a mutable object such as
   mutable.Queue, it will not be treated as the same object
   once its contents are changed.
   
   Therefore we take a different way: keeping the instances
   of ASThread and each ASThread has an Option[AnyRef] for
   which it is waiting.  The complexity is O(N) instead of
   O(1) for the number of ASThreads N, but we hope there is
   little impact because N must be small.
   */
  var waitingThreads = scala.collection.mutable.Map[ASThread, AnyRef]()
}


abstract class ASThread extends Thread {
  val token: Int = AccSched.getToken()

  // When the thread is terminated,
  // terminate() should be called.
  def terminate(): Unit = {
    AccSched.discardToken(token)
  }
}
