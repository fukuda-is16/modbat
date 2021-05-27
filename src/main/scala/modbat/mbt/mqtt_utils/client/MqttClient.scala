package modbat.mbt.mqtt_utils.client
import modbat.mbt.mqtt_utils.broker.MqttBroker
// import modbat.testlib.MBTThread

import accsched._

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
  var callbackHandlerThread: CHThread = _
  val messageQueue = scala.collection.mutable.Queue[(String, MqttMessage)]()

  def subscribe(topic: String, qos: Int = 1):Unit = {
    broker.subscribe(clientId, topic, qos)
  }

  def setCallback(cb: MqttCallback):Unit = {
    // message-arrived callback is handled by broker
    callback = cb
  }
// MBT Lock
// obj.wait() -> X.wait(obj)
// obj.notify()

  class CHThread extends ASThread {
    override def run() = {
      try {
        var topic: String = null
        var message: MqttMessage = null
        var endWhile = false
        while(!endWhile) {
          var ok = false
          callbackHandlerThread.synchronized {
            if (messageQueue.isEmpty) {
              ASThread.asWait(callbackHandlerThread)
              if (!isConnected) endWhile = false
            } else {
              val t = messageQueue.dequeue()
              topic = t._1
              message = t._2
              ok = true
            }
          }
          if (ok) callback.messageArrived(topic, message)
        }
      } catch {
        case e: InterruptedException =>
      }
    }
  }

  def connect(connOpts: MqttConnectOptions): Unit = {
    callbackHandlerThread = new CHThread
    isConnected = true
    callbackHandlerThread.start()
    MqttBroker.connect(this, clientId, dest)
  }

  // thread safe
  private[mqtt_utils] def enqueueMessage(topic: String, message: MqttMessage) = {
    callbackHandlerThread.synchronized {
      messageQueue += topic -> message
      AccSched.asNotifyAll(callbackHandlerThread)
    }
  }

  def getTopic(topic: String): MqttTopic = {
    // returns topic object, with which message can be published by publish method
    return new MqttTopic(this, topic)
  }

  def disconnect() = {
    callbackHandlerThread.synchronized {
      if (isConnected) {
        broker.disconnect(clientId)
        isConnected = false
        AccSched.asNotifyAll(callbackHandlerThread)
      }
    }
  }
}
