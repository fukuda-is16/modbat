package modbat.mbt.mqtt_utils.client

// currently this class exists just for compatibility and does not play any role
class MqttConnectOptions {
  var maxInflight = 1000000
  def setCleanSession(b: Boolean): Unit = {}
  def getMaxInflight: Int = maxInflight
  def setMaxInflight(n: Int): Unit = {
    maxInflight = n
  }
}
