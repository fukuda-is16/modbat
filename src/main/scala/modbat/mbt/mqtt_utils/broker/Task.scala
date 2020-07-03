package modbat.mbt.mqtt_utils.broker
import modbat.mbt.mqtt_utils.client.MqttClient
import scala.concurrent.duration._

sealed trait Task
case object Stop extends Task
case object Reset extends Task
case class Subscribe(id: String, topic: String) extends Task
case class Publish(topic: String, message: String, delay: FiniteDuration) extends Task
case class Connect(c: MqttClient, id: String) extends Task
case class Disconnect(id: String) extends Task
