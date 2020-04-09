class MqttTopic(client: MqttClient, topic: String) {
  def publish(message: String): Unit = {
    client.broker.publish(topic, string)
  }
}
