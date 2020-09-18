package Sleep
import modbat.testlib._

class Controller(meterNum: Int, sleepTime: Int) {
  var n = 0
  var watt = 0
  val timer = new Timer
  def main() = {
    //subscribe("m-report")
    val t = new Thread(timer)
    t.start()
    while(true) {
      val arrivedWatt = 1//listen().toDouble
      this.synchronized {
        if(n > 0) {
            val average = watt/n
            if(average * 100 < arrivedWatt || arrivedWatt * 100 < average) {
                //publish("c-alert", "meter may be broken")
            }
        }
        watt = watt //+ getMessage.toDouble
        n += 1
      }
    }
  }

  class Timer extends Runnable {
    def run() = {
      while(true) {
        SyncedThread.sleep(1000) // 止めて、起こす
        println(s"${watt}, ${n}");
        this.synchronized {
          n = 0; watt = 0
        }
      }
    }
  }
}
