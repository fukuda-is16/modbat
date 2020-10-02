//package Sleep
import modbat.dsl._
import modbat.testlib._

class SleepTest extends Model {
  var t: MBTThread = null
  "init" -> "aaa" := {
    t = new MBTThread(new Runnable{
      def run() = {
        println("test")
        MBTThread.sleep(1000)
        //Thread.sleep(1000)
        println("slept 1 sec")
      }
    }, "test thread")
    println(MBTThread.threadsToCheck)
    println("ok", t.getName)
    t.start()
  }
  "aaa" -> "end" := {
    println(MBTThread.threadsToCheck)
    println("end")
  } timeout 999
}
