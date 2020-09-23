package modbat.testlib
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import modbat.mbt.MBT

object MBTThread {
  val threadsToCheck = scala.collection.mutable.Set[MBTThread]()

  def sleep(millis: Long): Unit = {
    val mythd = Thread.currentThread().asInstanceOf[MBTThread]
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
      // wait for the main thread to exec the task scheduled above while advancing the virtual time
      mythd.wait()
    }
  }
}

class MBTThread(group: ThreadGroup = null, target: Runnable = new Runnable{def run() = ()}) extends Thread(group, target) {
  // > there’s no direct way to control which superclass constructor is called from an auxiliary constructor in a subclass.
  def this(target: Runnable) = this(null, target)
  def this(target: Runnable, name: String) = {this(null, target); super.setName(name)}
  def this(name: String) = {this(); super.setName(name)}
  def this(group: ThreadGroup, target: Runnable, name: String) = {this(group, target); super.setName(name)}
  def this(group: ThreadGroup, name: String) = {this(group); super.setName(name)}
  var blocked = false
  MBTThread.threadsToCheck.synchronized{MBTThread.threadsToCheck.push(this)}
}