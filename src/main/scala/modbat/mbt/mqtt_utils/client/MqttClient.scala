package modbat.mbt.mqtt_utils.client
import modbat.mbt.mqtt_utils.broker.MqttBroker

/*
object MqttClient {
  def generateClientId() = ???
}
*/

object MqttClient {
  var i: Int = 0
  def generateClientId() = {
    i += 1
    i.toString
  }
}

class MqttClient(dest: String, clientId: String) {
  var broker: MqttBroker = _
  var callback: MqttCallback = _
  var isConnected = false
  var callbackHandler: MBTThread = _
  val messageQueue = Queue

  def subscribe(topic: String, qos: Int = 1):Unit = {
    broker.subscribe(clientId, topic, qos)
  }

  def setCallback(cb: MqttCallback):Unit = {
    // message-arrived callback is handled by broker
    callback = cb
  }

  def connect(connOpts: MqttConnectOptions): Unit = {
    callbackHandler = MBTThread(new Runnable {
      def run() = {
        // if message queue is not empty, pass each message as argument to callback function
        // otherwise wait for message coming
        val myThread = Thread.currentThread
        
      }
    })
    MqttBroker.connect(this, clientId, dest)
    isConnected = true
  }

  def getTopic(topic: String): MqttTopic = {
    // returns topic object, with which message can be published by publish method
    return new MqttTopic(this, topic)
  }

  def disconnect() = {
    broker.disconnect(clientId)
    isConnected = false
  }

  class CallbackHandler extends Runnable {
    def run() = {
      ;
    }
  }
}
