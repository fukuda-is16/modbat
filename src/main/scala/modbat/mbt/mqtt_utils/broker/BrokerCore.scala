package modbat.mbt.mqtt_utils.broker
import scala.collection.mutable.{Queue, Map, Set}
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttMessage}


class BrokerCore extends Runnable {
  val tasks = Queue[Task]()
  val topics = Map[String, Set[String]]()
  val clientMap = Map[String, MqttClient]()
  var exit = false
  def run() = {
    while(!exit) {
      var t: Task = null
      tasks.synchronized {
        if (tasks.isEmpty) {
          tasks.wait()
        }
        t = tasks.dequeue()
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
    tasks.synchronized {
      tasks.clear()
      tasks += Stop
      tasks.notify()
    }
  }

  def reset(): Unit = {
    tasks.synchronized {
      tasks.clear()
      tasks += Reset
      tasks.notify()
    }
  }

  def regTask(t: Task): Unit = {
    tasks.synchronized {
      tasks += t
      tasks.notify()
    }
  }
}
