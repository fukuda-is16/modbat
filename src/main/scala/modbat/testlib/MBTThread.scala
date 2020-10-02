package modbat.testlib
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import modbat.mbt.MBT

object MBTThread {
  // MBTThread lock aquisition required
  val threadsToCheck = scala.collection.mutable.Set[MBTThread]()

  def sleep(millis: Long): Unit = {
    // my thread object, which is used for lock
    val mythd = Thread.currentThread().asInstanceOf[MBTThread]

    // register waking up task to scheduler
    MBT.time.scheduler.scheduleOnce(millis.milli) {
      mythd.synchronized {
        MBTThread.synchronized {mythd.blocked = false}
        mythd.notify()
      }
    }

    // turn to blocked state and wait on mythd
    mythd.synchronized {
      MBTThread.synchronized {mythd.blocked = true}
      mythd.notify()
      mythd.wait()
    }
  }
}

class MBTThread(group: ThreadGroup = null, target: Runnable = new Runnable{def run() = ()}) extends Thread(group, target) {
  // > there’s no direct way to control which superclass constructor is called from an auxiliary constructor in a subclass.
  // so below is added for making other types of constructor available
  def this(target: Runnable) = this(null, target)
  def this(target: Runnable, name: String) = {this(null, target); super.setName(name)}
  def this(name: String) = {this(); super.setName(name)}
  def this(group: ThreadGroup, target: Runnable, name: String) = {this(group, target); super.setName(name)}
  def this(group: ThreadGroup, name: String) = {this(group); super.setName(name)}

  // MBTThread lock aquisition required
  var blocked = false

  // register itself to check list
  MBTThread.synchronized{MBTThread.threadsToCheck += this}
}