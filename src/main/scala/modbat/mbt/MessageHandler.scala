package modbat.mbt
//import org.eclipse.paho.client.mqttv3._
//import org.eclipse.paho.client.mqttv3.persist._
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttCallback}
import modbat.mbt.mqtt_utils.broker.MqttBroker
import modbat.dsl._
import modbat.log.Log

object MessageHandler {
  // var clientId = MqttClient.generateClientId()
  // var broker = "tcp://localhost:1883"
  // var client: MqttClient = new MqttClient(broker, clientId)
  var client: MqttClient = new MqttClient()
//  val connOpts = new MqttConnectOptions()
  val timeToWait = -1
  var useMqtt = false
  @volatile var topics: Map[String, List[State]] = Map.empty
  @volatile var messages: Map[String, String] = Map.empty
  var arrivedTopic: Set[String] = Set.empty
  val mesLock = new AnyRef
  var defaultQos = 1

  var brokerRunning = false
  var broker: MqttBroker = _

  //subscribe topics described in test models
  def regTopic(topic: String, state: State, qos: Int = defaultQos) = {
    if(!useMqtt) {
      useMqtt = true
      connectMqttServer
    }
    if(topics.contains(topic)) {
      topics = topics + (topic -> (state :: topics(topic)))
    } else {
      topics = topics + (topic -> List(state))
      client.subscribe(topic, qos)
    }
  }

  def connectMqttServer = {
    if (!brokerRunning) {
      brokerRunning = true
      broker = new MqttBroker
      broker.start()
    }
//    connOpts.setCleanSession(true)
    client.setCallback(new Callback)
//    client.connect(connOpts)
    client.connect(broker)
    // Log.info("mqtt client connected to "+broker)
    Log.info("mqtt client connected to broker")
  }

  def publishRepeat(topic: String, msg: String, n: Int, qos: Int = defaultQos) {
    val mqttTopic = client.getTopic(topic)
    // val message = new MqttMessage(msg.getBytes())
    val message = msg
//    message.setQos(qos)
//    if(n > connOpts.getMaxInflight) connOpts.setMaxInflight(n)//avoid error: Too many publishes in progress (32202)
    // for(i <- 1 to n) mqttTopic.publish(message).waitForCompletion
    for(i <- 1 to n) mqttTopic.publish(message)
    Log.debug(s"published message $n time(s) to topic $topic: $msg")
  }

  def clear(): Unit = {
    if(useMqtt) {
      client.disconnect()
    }
    mesLock.synchronized {
      topics = Map.empty
      messages = Map.empty
      arrivedTopic = Set.empty
    }
    useMqtt = false
  }
  
  def stopBroker(): Unit = {
    if (brokerRunning) broker.stop()
  }

  class Callback extends MqttCallback {
    def connectionLost(e: Throwable) = {
      Log.info("connection lost")
      mesLock.synchronized {
        mesLock.notify()
      }
      e.printStackTrace
    }
    // def deliveryComplete(token: IMqttDeliveryToken) {
    def deliveryComplete() = {
      Log.debug(s"deliveryComplete")
    }

    // def messageArrived(topic: String, message: MqttMessage) = {
    def messageArrived(topic: String, message: String) = {
      //just store messages here, handle these messages in Modbat.allSuccStates
      val msg = message.toString
      Log.debug(s"(MessageHandler) message arrived from topic $topic: $msg")
      mesLock.synchronized {
        messages = messages + (topic -> msg)
        arrivedTopic += topic
        mesLock.notify()
      }
    }
  }
}
