package modbat.mbt.mqtt_utils.broker
import scala.collection.mutable.ArrayBuffer
import modbat.mbt.mqtt_utils.client.MqttClient

class BrokerCore extends Runnable {
  val tasks = ArrayBuffer[Task]()
  val topics = scala.collection.mutable.Set[String]()
  var client: MqttClient = _
  var exit = false
  def run() = {
    while(!exit) {
      var t: Task = _
      synchronized(tasks) {
        if (tasks.isEmpty) {
          wait()
        }
        t = tasks.remove(0)
      }
      doTask(t)
    }
  }

  def doTask(t: Task): Unit = match t {
    case Connect(c, cb) => client = c
    case s: Stop => exit = true
    case s: Subscribe(s) => topics += s
    case s: Publish => ???
  }


  def stop(): Unit = {
    synchronized(this) {
      tasks.clear()
      tasks += new Stop
      notify()
    }
  }

  def regTask(t: Task): Unit = {
    synchronized(this) {
      tasks.append(t)
    }
  }
}


}
