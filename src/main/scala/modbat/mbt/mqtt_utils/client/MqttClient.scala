package modbat.mbt.mqtt_utils.client
import modbat.mbt.mqtt_utils.broker.MqttBroker

/*
object MqttClient {
  def generateClientId() = ???
}
*/

class MqttClient(/*dest: MqttServer, clientId: Int = 1*/) {
  var broker: MqttBroker = _
  var callback: MqttCallback = _

  def subscribe(topic: String, qos: Int = 1):Unit = {
    broker.subscribe(topic, qos)
  }

  def setCallback(cb: MqttCallback):Unit = {
    // message-arrived callback is handled by broker
    callback = cb
  }

  def connect(b: MqttBroker): Unit = {
    broker = b
    broker.regClient(this)
  }

  def getTopic(topic: String): MqttTopic = {
    // returns topic object, with which message can be published by publish method
    return new MqttTopic(this, topic)
  }

  def disconnect() = {
    broker.reset()
  }
}
