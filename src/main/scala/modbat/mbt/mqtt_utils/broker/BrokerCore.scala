package modbat.mbt.mqtt_utils.broker
import scala.collection.mutable.{ArrayBuffer, Map, Set}
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttMessage}


class BrokerCore extends Runnable {
  val tasks = ArrayBuffer[Task]()
  val topics = Map[String, Set[String]]()
  val clientMap = Map[String, MqttClient]()
  var exit = false
  def run() = {
    while(!exit) {
      var t: Task = null
      this.synchronized {
        if (tasks.isEmpty) {
          this.wait()
        }
        t = tasks.remove(0)
      }
      doTask(t)
    }
  }

  def doTask(t: Task): Unit = t match {
    case Connect(c, id) => clientMap += (id -> c)
    case Disconnect(id) => {
      clientMap -= id
      for((topic, idSet) <- topics) idSet -= id
    }
    case Subscribe(id, topic) => {
      if (!(topics contains topic)) topics += (topic -> Set[String](id))
      else topics(topic) += id
    }
    case Publish(topic: String, message: String) => {
      if (topics contains topic) {
        for(id <- topics(topic)) {
          val client = clientMap(id)
          client.callback.messageArrived(topic, new MqttMessage(message.getBytes))
        }
      }
    }
    case Stop => exit = true
    case Reset => {
      topics.clear()
      clientMap.clear()
    }
  }


  def stop(): Unit = {
    this.synchronized {
      tasks.clear()
      tasks += Stop
      this.notify()
    }
  }

  def reset(): Unit = {
    this.synchronized {
      tasks.clear()
      tasks += Reset
      this.notify()
    }
  }

  def regTask(t: Task): Unit = {
    this.synchronized {
      tasks.append(t)
      this.notify()
    }
  }
}
