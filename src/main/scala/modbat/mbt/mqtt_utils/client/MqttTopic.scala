package modbat.mbt.mqtt_utils.client
import scala.concurrent.duration._

class MqttTopic(client: MqttClient, topic: String) {
  def publish(message: MqttMessage): MqttDeliveryToken = {
    client.broker.publish(topic, message)
    client.callback.deliveryComplete(new IMqttDeliveryToken)
    new MqttDeliveryToken()
  }
}
