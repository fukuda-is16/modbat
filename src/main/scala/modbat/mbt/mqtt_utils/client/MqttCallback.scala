trait MqttCallback {
  def connectionLost(e: Throwable): Unit
  // def deliveryComplete(token: IMqttDeliveryToken): Unit
  def deliveryComplete(): Unit
  def messageArrived(topic: String, message: MqttMessage): Unit
}
