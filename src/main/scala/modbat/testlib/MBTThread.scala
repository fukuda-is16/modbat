package modbat.testlib
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import modbat.mbt.MBT

object SyncedThread {
  val uncheckedThreads = scala.collection.mutable.ArrayStack[SyncedThread]()

  def sleep(millis: Long): Unit = {
    val mythd = Thread.currentThread().asInstanceOf[SyncedThread]
    mythd.synchronized {
      mythd.blocked = true
      MBT.time.scheduler.scheduleOnce(millis.milli) {
        uncheckedThreads.synchronized{uncheckedThreads.push(mythd)}
        mythd.synchronized {
          mythd.blocked = false
          mythd.notify()
        }
      }
      mythd.notify()
      mythd.wait()
    }
  }
}

class SyncedThread extends Thread {
  var blocked = false
  SyncedThread.uncheckedThreads.synchronized{SyncedThread.uncheckedThreads.push(this)}
}