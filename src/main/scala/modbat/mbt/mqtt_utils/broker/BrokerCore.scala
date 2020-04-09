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
      var t: Task = null
      this.synchronized {
        if (tasks.isEmpty) {
          wait()
        }
        t = tasks.remove(0)
      }
      doTask(t)
    }
  }

  def doTask(t: Task): Unit = t match {
    case Connect(c) => client = c
    case Stop => exit = true
    case Subscribe(s) => topics += s
    case Publish(topic: String, message: String) => {
      if (topics(topic)) {
        client.callback.messageArrived(topic, message)
      }
    }
  }


  def stop(): Unit = {
    this.synchronized {
      tasks.clear()
      tasks += Stop
      notify()
    }
  }

  def reset(): Unit = {
    this.synchronized {
      tasks.clear()
      notify()
    }
  }

  def regTask(t: Task): Unit = {
    this.synchronized {
      tasks.append(t)
      notify()
    }
  }
}
