package modbat.mbt.mqtt_utils.broker
import scala.collection.mutable.{Map, Set}
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttMessage}
import scala.concurrent.duration._

// thread safe for all methods

object MqttBroker {
  val brokerMap = collection.mutable.Map[String, MqttBroker]()
  def connect(c: MqttClient, id: String, dest: String) = {
    this.synchronized {
      var broker: MqttBroker = brokerMap get dest match {
        case None => {
          val b = new MqttBroker(dest)
          brokerMap(dest) = b
          b
        }
        case Some(b) => b
      }
      c.broker = broker
      broker.addClient(c, id)
    }
  }
  def reset(): Unit = {
    this.synchronized {
      for((_, broker) <- brokerMap) broker.reset()
    }
  }
}

class MqttBroker(dest: String) {
  val topicToClientIDs = Map[String, Set[String]]()
  val clientIDToInstance = Map[String, MqttClient]()

  def addClient(c: MqttClient, id: String):Unit = {
    this.synchronized {
      assert(!(clientIDToInstance contains id))
      clientIDToInstance(id) = c
    }
  }

  def disconnect(id: String): Unit = {
    this.synchronized {
      for((topic, ids) <- topicToClientIDs) ids -= id
      clientIDToInstance -= id
    }
  }

  def subscribe(id: String, topic: String, qos: Int = 1):Unit = {
    this.synchronized {
      if (topicToClientIDs contains topic) topicToClientIDs(topic) += id
      else topicToClientIDs(topic) = Set[String](id)
    }
  }

  def publish(topic: String, message: MqttMessage): Unit = {
    this.synchronized {
      if (topicToClientIDs contains topic) {
        for(id <- topicToClientIDs(topic)) {
          val c: MqttClient = clientIDToInstance(id)
          c.enqueueMessage(topic, message)
        }
      }
    }
  }

  def reset(): Unit = {
    this.synchronized {
      topicToClientIDs.clear()
      clientIDToInstance.clear()
    }
  }
/*
  def stop(): Unit = {
    if (running) {
      running = false
      brokerCore.stop()
      MqttBroker.brokerMap -= dest
    }
  }
*/
}
