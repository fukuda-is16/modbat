package modbat.mbt
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist._
import modbat.dsl._
import modbat.log.Log

object MessageHandler {
  var clientId = MqttClient.generateClientId()
  var broker = "tcp://localhost:1883"
  var client: MqttClient = new MqttClient(broker, clientId)
  val connOpts = new MqttConnectOptions()
  val timeToWait = -1
  var useMqtt = false
  @volatile var topics: Map[String, List[State]] = Map.empty
  @volatile var messages: Map[String, String] = Map.empty
  var defaultQos = 1

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
    connOpts.setCleanSession(true)
    client.setCallback(new Callback)
    client.connect(connOpts)
    Log.info("mqtt client connected to "+broker)
  }

  def publishRepeat(topic: String, msg: String, n: Int, qos: Int = defaultQos) {
    val mqttTopic = client.getTopic(topic)
    val message = new MqttMessage(msg.getBytes())
    message.setQos(qos)
    //avoid [ERROR]Too many publishes in progress (32202)
    if(n > connOpts.getMaxInflight) connOpts.setMaxInflight(n)
    for(i <- 1 to n) mqttTopic.publish(message).waitForCompletion
    Log.debug(s"published $msg $n times to $topic")
  }
  //TODO: テストが一回終わった時にコネクションを閉じたりtopicsを消去したりする
  def clear {
    if(useMqtt) {
      client.disconnect()
      //Log.debug("mqtt client disconnected")
    }
    topics = Map.empty
    useMqtt = false
  }
  //TODO:テストを終了するたびにスレッドも終了させる
  //TODO:リアルタイムにデバッグ情報を表示
  class Callback extends MqttCallback {
    def connectionLost(e: Throwable) {
      Log.info("connection lost")
      e.printStackTrace
    }
    def deliveryComplete(token: IMqttDeliveryToken) {
      Log.info(s"delivery complete")//: ${token.getMessage}")
    }
    def messageArrived(topic: String, message: MqttMessage) {
      val msg = message.toString
      Log.debug(s"(MessageHandler) message arrived from $topic: $msg")
      messages = messages + (topic -> msg)
      if(topics.contains(topic)) {
        for(s <- topics(topic)) {
          s.messageArrived(topic, msg.toString)
        }
      } else {
        Log.error(s"Subscribing topic $topic, but no transition is waiting for it.")
      }
    }
  }
}