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
  var arrivedTopic: Set[String] = Set.empty
  val mesLock = new AnyRef
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
    Log.debug(s"published $msg $n time(s) to $topic")
  }
  //TODO: テストが一回終わった時にコネクションを閉じたりtopicsを消去したりする
  def clear {
    if(useMqtt) {
      client.disconnect()
      //Log.debug("mqtt client disconnected")
    }
    mesLock.synchronized {
      topics = Map.empty
      messages = Map.empty
      arrivedTopic = Set.empty
    }
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
    /*
     * TODO: 来たことをメインスレッドに教えるだけにして、処理はメインスレッドで行うほうが安全
     */
    def messageArrived(topic: String, message: MqttMessage) {
      //just store messages here, handle these messages in Modbat.allSuccStates
      val msg = message.toString
      Log.debug(s"(MessageHandler) message arrived from $topic: $msg")
      mesLock.synchronized {
        messages = messages + (topic -> msg)
        arrivedTopic += topic
      }
    }
  }
}