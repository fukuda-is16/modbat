package modbat.mbt
// import org.eclipse.paho.client.mqttv3._
// import org.eclipse.paho.client.mqttv3.persist._
import modbat.mbt.mqtt_utils.client.{MqttClient, MqttCallback, MqttConnectOptions, MqttMessage, IMqttDeliveryToken}
import modbat.mbt.mqtt_utils.broker.MqttBroker
import modbat.dsl._
import modbat.log.Log
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object MessageHandler {
  var clientId = MqttClient.generateClientId()
  var broker = "tcp://localhost:1883"
  var client: MqttClient = new MqttClient(broker, clientId)
  val connOpts = new MqttConnectOptions()
  val timeToWait = -1
  var useMqtt = false
  @volatile var topics: Map[String, List[State]] = Map.empty
  //@volatile var messages: Map[String, String] = Map.empty
  //var arrivedTopic: Set[String] = Set.empty
  val arrivedMessages = scala.collection.mutable.Queue[(State, String, String)]() // (state, topic, message)
  val mesLock = new AnyRef
  var defaultQos = 1

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
    connOpts.setCleanSession(true)
    client.setCallback(new Callback)
    client.connect(connOpts)
    Log.info("mqtt client connected to "+broker)
  }

  def publishRepeat(topic: String, msg: String, n: Int, delay: FiniteDuration, qos: Int = defaultQos) {
    val mqttTopic = client.getTopic(topic)
    val message = new MqttMessage(msg.getBytes())
    message.setQos(qos)
    if(n > connOpts.getMaxInflight) connOpts.setMaxInflight(n)//avoid error: Too many publishes in progress (32202)
    for(i <- 1 to n) mqttTopic.publish(message, delay).waitForCompletion
    Log.debug(s"published message $n time(s) to topic $topic: $msg")
  }

  def clear():Unit = {
    if(useMqtt) {
      client.disconnect()
    }
    mesLock.synchronized {
      topics = Map.empty
      // messages = Map.empty
      // arrivedTopic = Set.empty
      arrivedMessages.clear()
    }
    useMqtt = false
    MqttBroker.reset()
  }

  class Callback extends MqttCallback {
    def connectionLost(e: Throwable) {
      Log.info("connection lost")
      mesLock.synchronized {
        mesLock.notify()
      }
      e.printStackTrace
    }
    def deliveryComplete(token: IMqttDeliveryToken) {
      Log.debug(s"deliveryComplete")
    }

    def messageArrived(topic: String, message: MqttMessage) {
      //just store messages here, handle these messages in Modbat.allSuccStates
      val msg = message.toString
      Log.debug(s"(MessageHandler) message arrived from topic $topic: $msg")
      mesLock.synchronized {
        // messages = messages + (topic -> msg)
        // arrivedTopic += topic
        // トピックを待っているモデルmodelを探索する、遅延時間delayを設定、(model, topic, message)を情報として持つようなタスクを作ってmock schedularに渡す
        if (topics contains topic) for(state <- topics(topic)) {
          import MBT.rng
          val dmin = state.model.model.rcvDelayMin
          val dmax = state.model.model.rcvDelayMax
          val interval = dmax - dmin
          val delay = dmin + (if (interval > 0) rng.nextInt(interval + 1) else 0)
          // delay is distributed over [dmin, dmax]
          if (delay > 0) MBT.time.scheduler.scheduleOnce(delay.millis){
            arrivedMessages += Tuple3(state, topic, msg)
          } else {
            arrivedMessages += Tuple3(state, topic, msg)
          }
        }
        mesLock.notify()
      }
    }
  }
}
