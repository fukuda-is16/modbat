package modbat.mbt.mqtt_utils.broker
package modbat.mbt.mqtt_utls.client

sealed trait Task
case object Stop extends Task
case class Subscribe(s: string) extends Task
case class Connect(c: MqttClient) extends Task
case class Publish(topic: String, message: String) extends Task
