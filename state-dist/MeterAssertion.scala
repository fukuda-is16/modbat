import modbat.dsl._
// import org.eclipse.paho.client.mqttv3._
// import org.eclipse.paho.client.mqttv3.persist._
import modbat.mbt.mqtt_utils.client._
import modbat.mbt.mqtt_utils.broker._
import java.net._
import java.io._

class MeterAssertion extends Model {
  var user: User = _
  "init" -> "run" := {
    user = new User()
    launch(user)
  }
  "run" -> "end" := {
    publish("end","endMessage")
  } timeout 10000
}

class User extends Model {
  val meterNum = 10
  var controller: Controller = _
  val sleepTime = 1000
  var meters: Meter = _
  var brokenMeter = 0

  "setup" -> "wait" := {
    controller = new Controller(meterNum, sleepTime)
    controller.connect
    meters = new Meter(meterNum)
    launch(meters)
  }
  "wait" -> "branch" := {} timeout (1000,2000)
  "branch" -> "watt" := {
    publish("u-watt", "check watt")
    brokenMeter = meters.instanceNumInStates(List("broken"))
  }
  "branch" -> "check" := {
    publish("u-check", "check if meter is broken")
    brokenMeter = meters.instanceNumInStates(List("broken"))
  }
  "watt" -> "wait" := {
    if(getMessage != "no report from meters")
      assert(getMessage.toDouble == meters.watt)
  } subscribe ("c-watt")
  "check" -> "wait" := {
    if(meters.instanceNumInStates(List("run", "break?")) == meterNum)
      assert(getMessage == "all alive")
  } subscribe("c-check")
  List("watt", "check") -> "fail" := {
    controller.disconnect
    assert(false)
  } realTimeout(sleepTime + 1000)
  List("wait", "branch", "watt", "check") -> "end" := {
    //controller.disconnect
  } subscribe "end"
}

class Meter(n: Int = 1) extends Model {
  setInstanceNum(n)
  var watt = 1.0

  "run" -> "break?" := {
    publish("m-report", watt.toString)
  } timeout (1000, 1500) label "regular-report"
  "run" -> "break?" := {
    publish("m-report", watt.toString)
  } subscribe "c-reportNow"
  "break?" -> "run":= {} weight 0.9
  "break?" -> "broken" := {} weight 0.1
  
  List("run", "break?", "broken") -> "end":= {} subscribe "end"
}

//SUT
class Controller (meterNum: Int, sleepTime: Int) {
  val watt: Double = 1.0
  val brokenWatt: Double = 100.0
  val broker = "tcp://localhost:1883"
  var clientId: String = _ 
  var client: MqttClient = _
  var connopts: MqttConnectOptions = _
  var n = 0
  var sum = 0.0
  val lock = new AnyRef()

  def connect {
    clientId = MqttClient.generateClientId()
    client = new MqttClient(broker, clientId)
    connopts = new MqttConnectOptions()
    connopts.setCleanSession(true)
    client.connect(connopts)
    println("controller connect")
    client.setCallback(new Callback)
    client.subscribe("end")
    client.subscribe("u-watt")
    client.subscribe("u-check")
    client.subscribe("m-report")
    println("controller subscribed")
  }
  def disconnect = client.disconnect()
  def publishMessage(topicName: String, message: String) {
    val t = client.getTopic(topicName)
    val msg: MqttMessage = new MqttMessage(message.getBytes())
    if(client.isConnected) {
      t.publish(msg)
      println(s"controller: published to topic $topicName: $message")
    } else println("controller: Client is disconnected. Cannot publish.")
  }

  //1st function: wait meters' report and calculate avarage watt
  def reportWatt {
    lock.synchronized {
      n = 0
      sum = 0.0
    }
    println(s"controller: start sleep ($sleepTime ms)")
    Thread.sleep(sleepTime)
    println("controller: finish sleep")
    var m = ""
    lock.synchronized {
      if(n>0) {
        m = (sum / n).toString
      } else {
        m = "no report from meters"
      }
    }
    publishMessage("c-watt", m)
  }
  //2nd function: detect broken meter and alarm
  def meterAliveCheck {
    lock.synchronized {
      publishMessage("c-reportNow", "report watt now")
      n = 0
      sum = 0
      println(s"controller: wait meter for $sleepTime ms")
      var t = System.currentTimeMillis
      val finishT = t + sleepTime
      while(t < finishT) {
        lock.wait(finishT - t)
        t = System.currentTimeMillis
      }
    }
    println("controller: finish wait")
    val broken = meterNum - n
    if(broken == 0) {//TODO: branch
      publishMessage("c-check", "all alive")
    } else {
      publishMessage("c-check", s"$broken meter(s) are broken")
    }
  }

  class Callback extends MqttCallback {
    def connectionLost(e: Throwable) {
      println("controller: connection lost")
    }
    def deliveryComplete(token: IMqttDeliveryToken) {
    }
    def messageArrived(topic: String, message: MqttMessage) {
      println(s"controller: message $message arrived from topic $topic")
      topic match {
        case "end" =>
          if(client.isConnected) client.disconnect()
        case "u-watt" => 
          reportWatt
        case "u-check" =>
          meterAliveCheck
        case "m-report" =>
          lock.synchronized {
            n += 1
            sum += message.toString.toDouble
            println(s"controller: n = $n, sum = $sum")
            if(n >= meterNum) lock.notify()
          }
        case _ =>
          println(s"message from unexpected topic $topic")
      }
    }
  }
}
