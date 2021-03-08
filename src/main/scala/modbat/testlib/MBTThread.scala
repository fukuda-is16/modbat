package modbat.testlib
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import modbat.mbt.MBT

import modbat.log.Log

object MBTThread {
  // MBTThread lock aquisition required
  val threadsToCheck = scala.collection.mutable.Set[MBTThread]()

  def sleep(millis: Long): Unit = {
    if (millis <= 0) return
    // my thread object, which is used for lock
    val mythd = Thread.currentThread().asInstanceOf[MBTThread]

    // register waking up task to scheduler
    //Log.info(s"thread ${Thread.currentThread} starts sleeping for ${millis.milli} from ${MBT.time.elapsed}")
    MBT.time.scheduler.scheduleOnce(millis.milli) {
      mythd.synchronized {
        MBTThread.synchronized {mythd.blocked = false}
        import modbat.mbt.MBT
        //Log.info(s"awake thread ${MBT.time.elapsed}")
        mythd.notify()
      }
    }
    //Log.info(s"thread ${Thread.currentThread} waked up; current time is ${MBT.time.elapsed}")
    // turn to blocked state and wait on mythd
    mythd.synchronized {
      MBTThread.synchronized {mythd.blocked = true}
      //Log.info(s"wait ${MBT.time.elapsed}, ${mythd}")
      mythd.notify()
      mythd.wait()
      //Log.info(s"wait finished ${MBT.time.elapsed}, ${mythd}")
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