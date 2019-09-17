import modbat.dsl._
import modbat.mbt.MBT
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist._
import java.net._
import java.io._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class House extends Model {
  val seed = "%x".format(getRandomSeed)
  val host = "tcp://localhost:"

  // Field
  val height = 4
  val width = 4
  var temperatureManager: TemperatureManager = _
  val timeScale = 0.0001
  val envs = Array(new Environment(s"${seed}-0"),
    new Environment(s"${seed}-1"))

  // Clients
  var controller: Controller = _
  val thermometer = Array.ofDim[Thermometer](height, width)
  val airConditioner = Array.ofDim[AirConditioner](height, width)
  var running = false
  var end = false
  val timeToWait = -1

  "init" -> "standby" := {
    controller = new Controller()
    launch(controller)
    temperatureManager = new TemperatureManager()
    launch(temperatureManager)
    for(i <- 0 until height) {
      for(j <- 0 until width) {
        thermometer(i)(j) = new Thermometer(i, j)
        launch(thermometer(i)(j))
        airConditioner(i)(j) = new AirConditioner(i, j)
        launch(airConditioner(i)(j))
      }
    }
  } label "setup"
  "standby" -> "run" := {
    require(controller.ready && thermometer.forall(_.forall(_.ready)))
    running = true
  } label "run" stay scale(3000)
  "run" -> "end" := {
    end = true
  } label "end"

  // returns time in millisecond
  def scale(timeInSecond: Double): Int = {
    (timeInSecond * timeScale * 1000).asInstanceOf[Int]
  }
  
  object Environment {
    val conductionOuter = 0.0001
    val conductionInner = 0.0002
    val airConditionerRate = 0.001
    val dirs = Array((-1,0), (1,0), (0,-1), (0,1))
    var temperatureOut: Double = 20.0
    var temperatureRate: Double = 0.0
    val interval: Double = 60.0
  }

  class Environment(val prefix: String) {
    val temperatures = Array.fill[Double](height, width){Environment.temperatureOut}
    val acMode = Array.fill[Int](height, width){0}
    val writer = new OutputStreamWriter(new FileOutputStream(s"../log/House/${prefix}.dat"))
    var maxDiff = 0.0

    def getTemperatureAndConduction(ts: Array[Array[Double]], i: Int, j: Int): (Double, Double) = {
      if(i >= 0 && i < height && j >= 0 && j < width) {
        (temperatures(i)(j), Environment.conductionInner)
      } else {
        (Environment.temperatureOut, Environment.conductionOuter)
      }
    }
    
    def updateTemperature() {
      val ts0 = temperatures.map(_.clone())
      for(i <- 0 until height) {
        for(j <- 0 until width) {
          val currentT = temperatures(i)(j)
          var diff = 0.0
          for((di, dj) <- Environment.dirs) {
            val si = i + di
            val sj = j + dj
            val (t, cond) = getTemperatureAndConduction(ts0, si, sj)
            diff += (t - currentT) * cond
          }
          diff += acMode(i)(j) * Environment.airConditionerRate
          temperatures(i)(j) += diff * Environment.interval
        }
      }
      Environment.temperatureOut += Environment.temperatureRate * Environment.interval
    }

    def dumpTemperature() {
      writer.write(s"# ${Environment.temperatureOut} ${controller.target} ${maxDiff}\n")
      for(i <- 0 until height) {
        for(j <- 0 until width) {
          writer.write(s"${j} ${i} ${temperatures(i)(j)} ${acMode(i)(j)}\n")
        }
        writer.write("\n")
      }
      writer.write("\n")
    }

    def checkTemperature() {
      for(row <- temperatures) {
        for(t <- row) {
          maxDiff = maxDiff max (t - controller.target).abs
        }
      }
    }
  }

  class Controller extends Model {
    val broker = host + 1883
    val id = seed + "-controller"
    val tempQos = 0
    val acQos = 2
    var target: Double = 20.0
    var ready = false
    val interval = 400.0
    val instance = Array(
      new Instance(envs(0)),
      new FragileInstance(envs(1)))
    val sensitivity = 1.0


    "init" -> "run" := {
      instance.foreach(_.connect())
      /* register task to schedule instead of updater.start() */
      MBT.time.scheduler.scheduleOnce(0.millis)(updater())

      ready = true
    } label "connect"
    "run" -> "run" := {
      require(running)
      target += choose(-1, 1)
    } label "set temperature" stay (scale(300), scale(500))
    "run" -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    def updater() {
      instance.foreach(_.control())
      if(!end) MBT.time.scheduler.scheduleOnce(scale(interval).millis)(updater())
    }


    class Instance(val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-controller"
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      connopts.setMaxInflight(height * width)
      val measuredTemperatures = Array.ofDim[Double](height, width)

      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
          e.printStackTrace
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        def messageArrived(topic: String, msg: MqttMessage) {
          val topics = topic.split('/')
          val x = topics(2).toInt
          val y = topics(3).toInt
          measuredTemperatures(x)(y) = msg.toString.toDouble
        }
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
        client.subscribe(s"${prefix}/temperature/+/+", tempQos).waitForCompletion(timeToWait)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      def setAc(i: Int, j: Int, value: Int): IMqttDeliveryToken = {
        val message = new MqttMessage(value.toString().getBytes())
        message.setQos(acQos)
        val topic = s"${prefix}/ac-control/${i}/${j}"
        client.publish(topic, message)
      }
      def control() {
        var dtokens: List[IMqttDeliveryToken] = Nil
        for(i <- 0 until height) {
          for(j <- 0 until width) {
            val t = measuredTemperatures(i)(j)
            if(t < target - sensitivity) {
              dtokens ::= setAc(i, j, 1)
            } else if(t > target + sensitivity) {
              dtokens ::= setAc(i, j, -1)
            } else {
              dtokens ::= setAc(i, j, 0)
            }
          }
        }
        dtokens.foreach(_.waitForCompletion(timeToWait))
      }
    }
    class FragileInstance(env: Environment)
        extends Instance(env) {
    }
  }

  class TemperatureManager extends Model {
    def updater() {
      envs.foreach(_.updateTemperature())
      envs.foreach(e => {
        e.dumpTemperature()
        e.checkTemperature()
      })
      if(!end) MBT.time.scheduler.scheduleOnce(scale(Environment.interval).millis)(updater())
    }


    "init" -> "0" := {
      /* register task to schedule instead of updater.start() */
      MBT.time.scheduler.scheduleOnce(0.millis)(updater())

    } label "setup"
    List("0", "+") -> "+" := {
      require(running)
      Environment.temperatureRate = 0.001
    } label "set +" stay (scale(200), scale(500))
    List("0", "+", "-") -> "0" := {
      require(running)
      Environment.temperatureRate = 0
    } label "set 0" stay (scale(200), scale(500))
    List("0", "-") -> "-" := {
      require(running)
      Environment.temperatureRate = -0.001
    } label "set -" stay (scale(200), scale(500))
    List("0", "+", "-") -> "end" := {
      require(end)
    } label "end"
  }

  class Thermometer(val i: Int, val j: Int, val port: Int = 1883) extends Model {
    val broker = host + port
    val qos = 0
    val interval = 300.0
    var ready = false
    var broken = false
    val instance = Array(
      new Instance(i, j, envs(0)),
      new FragileInstance(i, j, envs(1)))

    "init" -> "run" := {
      instance.foreach(_.connect())
      ready = true
      /* register task to schedule instead of publisher.start() */
      MBT.time.scheduler.scheduleOnce(0.millis)(publisher())
    } label "connect"
    List("run", "broken") -> "broken" := {
      require(running)
      broken = true
    } label "break" weight 0.1 stay (scale(200), scale(500))
    List("run", "broken") -> "run" := {
      require(running)
      broken = false
    } label "restore" weight 0.9 stay (scale(200), scale(500))
    List("run", "broken") -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    def publisher() {
      instance.foreach(_.publish())
      if(!end) MBT.time.scheduler.scheduleOnce((instance(0).sleepTime + instance(1).sleepTime + scale(interval)).millis)(publisher())
    }


    class Instance(val i: Int, val j: Int, val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-thermo-${i}-${j}"
      val topic = s"${prefix}/temperature/${i}/${j}"
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)
      val qos = 0
      val normalRange = 1.0
      val brokenRange = 1.0
      var sleepTime = 0//variable to implement virtual time sleep

      def getTemperature(): Double = {
        val range = if(broken) brokenRange else normalRange
        env.temperatures(i)(j) + choose(-100, 100) / 100.0 * range
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      def publish() {
        if(!client.isConnected()) {
          sleepTime = 0
          return
        }
        val message = new MqttMessage(getTemperature().toString.getBytes())
        message.setQos(qos)
        try {
          client.publish(topic, message).waitForCompletion(timeToWait)
          sleepTime = scale(interval)
        } catch {
          case e: Exception => {
            System.err.println(id)
            e.printStackTrace
          }
        }
      }
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        def messageArrived(topic: String, msg: MqttMessage) {}
      }
    }
    class FragileInstance(i: Int, j: Int, env: Environment)
        extends Instance(i, j, env) {
      override val brokenRange = 10.0
    }
  }

  class AirConditioner(val i: Int, val j: Int, val port: Int = 1883) extends Model {
    val broker = host + port
    val qos = 2
    var ready = false
    var broken = false
    val instance = Array(
      new Instance(i, j, envs(0)),
      new FragileInstance(i, j, envs(1)))

    "init" -> "run" := {
      instance.foreach(_.connect())
      ready = true
    } label "connect"
    List("run", "broken") -> "broken" := {
      require(running)
      broken = true
    } label "break" weight 0.1 stay (scale(200), scale(500))
    List("run", "broken") -> "run" := {
      require(running)
      broken = false
    } label "restore" weight 0.9 stay (scale(200), scale(500))
    List("run", "broken") -> "end" := {
      require(end)
      instance.foreach(_.disconnect())
    } label "end"

    class Instance(val i: Int, val j: Int, val env: Environment) {
      val prefix = env.prefix
      val id = s"${prefix}-ac-${i}-${j}"
      val topic = s"${prefix}/ac-control/${i}/${j}"
      var storedMode: Int = 0
      val client = new MqttAsyncClient(broker, id)
      val connopts = new MqttConnectOptions()
      connopts.setCleanSession(false)

      def setMode(newMode: Int) {
        storedMode = newMode
        env.acMode(i)(j) = newMode
      }
      def connect() {
        client.setCallback(listner)
        client.connect(connopts).waitForCompletion(timeToWait)
        client.subscribe(topic, qos)
      }
      def disconnect() {
        client.disconnect().waitForCompletion(timeToWait)
      }
      object listner extends MqttCallback {
        def connectionLost(e: Throwable) {
          println("connection lost: " + id)
          e.printStackTrace
        }
        def deliveryComplete(token: IMqttDeliveryToken) {}
        def messageArrived(topic: String, msg: MqttMessage) {
          println(s"$id recieved $msg")
          setMode(msg.toString.toInt)
        }
      }
    }
    class FragileInstance(i: Int, j: Int, env: Environment)
        extends Instance(i, j, env) {
      override def setMode(newMode: Int) {
        storedMode = newMode
        env.acMode(i)(j) = if(broken) choose(-1, 1) else newMode
      }
    }
  }

  @After
  def after() {
    envs.foreach(_.writer.close())
  }
}
