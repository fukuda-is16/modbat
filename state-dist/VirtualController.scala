import modbat.dsl._
import org.eclipse.paho.client.mqttv3._
import org.eclipse.paho.client.mqttv3.persist._
import java.net._
import java.io._

object Const {
    val scale = 1 * 60 * 3 / 10 //10min
    val hour = 60*60*1000 / scale
    val min = 60*1000 / scale
    val watt = 1.0
    val brokenWatt = 1000.0
}

class VirtualController extends Model {
  var user: User = _
  "init" -> "run" := {
    user = new User()
    launch(user)
  }
  "run" -> "waitR" := {
    publish("end","endMessage")
  } timeout (3 * Const.hour)
  "waitR" -> "end" := {
  } realTimeout 1000
}

class User extends Model {
/*
 * meterNum=1 real	0m8.215s user	0m1.692s sys	0m0.139s
 * meterNum=10 real	0m16.376s user	0m2.963s sys	0m0.276s
 * meterNum=100 real	1m6.629s user	0m7.430s sys	0m1.635s
 * meterNum=200 real	1m32.225s user	0m8.323s sys	0m2.674s


 */

  val meterNum = 1000
  var controller: Controller = _
  val sleepTime = 1000
  var meters: Meter = _
  var brokenMeter = 0
  var alarmCounter = 0

  def someMeterBroken : Boolean = {
    meters.hasInstanceInStates(List("broken", "end"))
  }

  "setupC" -> "setupM" := {
    controller = new Controller(meterNum, sleepTime)
    launch(controller)
  } 
  "setupM" -> "wait" := {
    meters = new Meter(meterNum)
    launch(meters)
  }
  "wait" -> "wait" := {
      alarmCounter += 1
      assert(someMeterBroken)
  } subscribe "c-alert"
  "wait" -> "stop" := {
        assert(alarmCounter > 0)
  } subscribe "end"
}

class Meter(n: Int = 1) extends Model {
  setInstanceNum(n)
  var watt = 1.0

  "wait" -> "run" := {} timeout (10*Const.min, 20*Const.min)
  "run" -> "break?" := {
    publish("m-report", Const.watt.toString)
  } timeout (20*Const.min) label "regular-report"
  "run" -> "break?" := {
    publish("m-report", Const.watt.toString)
  } subscribe "c-reportNow"
  "break?" -> "run":= {} weight 0.9
  "break?" -> "broken" := {} weight 0.1

  "broken" -> "broken" := {
    publish("m-report", Const.brokenWatt.toString)
  } timeout (20*Const.min) label "regular-report"

  List("run", "break?", "broken") -> "end":= {} subscribe "end"
}

class Timer extends Model {
    "wait" -> "start" := {} subscribe "timerStart"
    "start" -> "waitReal" := {
    } timeout Const.hour
    "waitReal1" -> "waitReal2" := { publish("timerRing", "1 hour")} realTimeout 1000
    "waitReal2" -> "wait" := {} realTimeout 1000
}

class Controller(meterNum: Int, sleepTime: Int) extends Model {
    var timer: Timer = _
    var watt: Double = 0.0
    var n: Int = 0
    "init" -> "set" := {
        timer = new Timer()
        launch(timer)
    }
    "set" -> "wait" := {
        watt = 0
        n = 0
        publish("timerStart", "timer start")
        println("controller: timer start")
    }
    "wait" -> "wait" := {
        println("controller: message from m-report: "+ getMessage)
        val arrivedWatt = getMessage.toDouble
        if(n > 0) {
            val average = watt/n
            println(s"controller:compare $getMessage with average(= $average)")
            if(average * 100 < arrivedWatt || arrivedWatt * 100 < average) {
                publish("c-alert", "meter may be broken")
                println("controller: published alert")
            }
        }
        watt = watt + getMessage.toDouble
        n += 1
    } subscribe "m-report"
    "wait" -> "set" := {
        publish("c-report", watt.toString + ", " + n.toString) //合計値
    } subscribe "timerRing"
    
    List("wait", "set") -> "end" := {} subscribe "end"
}