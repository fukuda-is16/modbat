import modbat.testlib._
import modbat.mbt.mqtt_utils.client._

// Controller test MBTThread
// new MBTThread(new Controller)

class Controller(meterNum: Int, sleepTime: Int) extends Runnable {
  var n = 0
  var watt = 0
  val timer = new Timer
  val c = new MqttClient("tcp://localhost:1883", MqttClient.generateClientId())
  val lock = new AnyRef
  @volatile var running = true

  def run() = {
    val connOpts = new MqttConnectOptions()
    connOpts.setCleanSession(true)
    c.setCallback(new Callback)
    c.connect(connOpts)
    c.subscribe("m-report", 1)
    c.subscribe("end", 1)
    while(running) {
      MBTThread.sleep(1000)
      lock.synchronized {
        n = 0
        watt = 0
      }
    }
  }

  class Callback extends MqttCallback {
    def connectionLost(e: Throwable): Unit = {}
    def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
    def messageArrived(topic: String, message: MqttMessage): Unit = {
      if (topic == "end") {
        running = false
        return
      }
      val arrivedWatt = message.toString.toDouble
      
      lock.synchronized {
        if (n > 0) {
          val average = watt/n
          if(average * 100 < arrivedWatt || arrivedWatt * 100 < average) {
            val mqttTopic = c.getTopic("c-alert")
            val message = new MqttMessage("meter may be broken".getBytes())
            message.setQos(1)
            mqttTopic.publish(message)
          }
        }
        watt += arrivedWatt
        n += 1
      }
    }
  }
}
