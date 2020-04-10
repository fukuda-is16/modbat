package modbat.mbt.mqtt_utils.client

class MqttTopic(client: MqttClient, topic: String) {
  def publish(message: MqttMessage): MqttDeliveryToken = {
    client.broker.publish(topic, message)
    client.callback.deliveryComplete(new IMqttDeliveryToken)
    new MqttDeliveryToken()
  }
}
