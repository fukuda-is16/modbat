package modbat.mbt.mqtt_utils.client

trait MqttCallback {
  def connectionLost(cause: Throwable): Unit
  def deliveryComplete(token: IMqttDeliveryToken): Unit
  def messageArrived(topic: String, message: MqttMessage): Unit
}
