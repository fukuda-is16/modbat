package modbat.mbt.mqtt_utils.client

trait MqttCallback {
  def connectionLost(e: Throwable): Unit
  def deliveryComplete(): Unit
  def messageArrived(topic: String, message: String): Unit
}
