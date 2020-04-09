package modbat.mbt.mqtt_utils.broker
import modbat.mbt.mqtt_utils.client.MqttClient

sealed trait Task
case object Stop extends Task
case class Subscribe(s: String) extends Task
case class Connect(c: MqttClient) extends Task
case class Publish(topic: String, message: String) extends Task
