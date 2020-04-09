package modbat.mbt.mqtt_utils.broker
import modbat.mbt.mqtt_utils.client.MqttClient

class MqttBroker {
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

  def subscribe(s: String, qos: Int = 1):Unit = {
    assert(running)
    brokerCore.regTask(new Subscribe(s))
  }

  def publish(topic: String, message: String): Unit = {
    assert(running)
    brokerCore.regTask(new Publish(topic, message))
  }

  def stop(): Unit = {
    if (running) {
      running = false
      brokerCore.stop()
    }
  }

  def reset(): Unit = {
    if (running) {
      brokerCore.reset()
    }
  }

  def regClient(c: MqttClient):Unit = {
    assert(running)
    brokerCore.regTask(new Connect(c))
  }
}
