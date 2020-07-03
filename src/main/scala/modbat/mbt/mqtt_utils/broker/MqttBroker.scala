package modbat.mbt.mqtt_utils.broker
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttMessage}
import scala.concurrent.duration._

object MqttBroker {
  val brokerMap = collection.mutable.Map[String, MqttBroker]()
  def connect(c: MqttClient, id: String, dest: String) = {
    var broker: MqttBroker = brokerMap get dest match {
      case None => {
        val bt = new MqttBroker(dest)
        bt.start()
        brokerMap(dest) = bt
        bt
      }
      case Some(b) => b
    }
    c.broker = broker
    broker.addClient(c, id)
  }
  def reset(): Unit = {
    for((_, broker) <- brokerMap) broker.reset()
  }
}


class MqttBroker(dest: String) {
  var running: Boolean = false
  var brokerCore = new BrokerCore()
  var thread: Thread = _

  def start(): Unit = {
    if (!running) {
      running = true
      thread = new Thread(brokerCore)
      thread.start()
    } else {
      brokerCore.reset()
    }
  }

  def subscribe(id: String, topic: String, qos: Int = 1):Unit = {
    assert(running, "MqttBroker is not running")
    brokerCore.regTask(new Subscribe(id, topic))
  }

  def publish(topic: String, message: MqttMessage, delay: FiniteDuration = 0.millis): Unit = {
    assert(running, "MqttBroker is not running")
    brokerCore.regTask(new Publish(topic, new String(message.bytes), delay))
  }

  def stop(): Unit = {
    if (running) {
      running = false
      brokerCore.stop()
      MqttBroker.brokerMap -= dest
    }
  }

  def reset(): Unit = {
    if (running) {
      brokerCore.reset()
    }
  }

  def disconnect(id: String): Unit = {
    assert(running)
    brokerCore.regTask(new Disconnect(id))
  }

  def addClient(c: MqttClient, id: String):Unit = {
    assert(running)
    brokerCore.regTask(new Connect(c, id))
  }
}
