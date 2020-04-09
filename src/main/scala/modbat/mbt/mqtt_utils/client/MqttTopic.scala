package modbat.mbt.mqtt_utils.client

class MqttTopic(client: MqttClient, topic: String) {
  def publish(message: String): Unit = {
    client.broker.publish(topic, message)
    client.callback.deliveryComplete()
  }
}
