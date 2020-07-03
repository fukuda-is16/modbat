package modbat.mbt.mqtt_utils.client
import scala.concurrent.duration._

class MqttTopic(client: MqttClient, topic: String) {
  def publish(message: MqttMessage, delay: FiniteDuration = 0.millis): MqttDeliveryToken = {
    client.broker.publish(topic, message, delay)
    client.callback.deliveryComplete(new IMqttDeliveryToken)
    new MqttDeliveryToken()
  }
}
