package modbat.mbt.mqtt_utils.client

class MqttMessage(val bytes: Array[Byte]) {
  def setQos(qos: Int) = {}
  override def toString(): String = new String(bytes)
}
