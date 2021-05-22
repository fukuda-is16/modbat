import modbat.dsl._

object Const {
  val meterNum = 3

  val sec = 1000
  val min = 60 * sec
  val hour = 60 * min
}

class VC extends Model {
  import Const._

  "init" -> "run" := {
    val user = new User()
    launch(user)
  }
  
  "run" -> "waitR" := {
    publish("end","endMessage")
  } timeout (3 * Const.hour)

  // 待つだけ。実際は今の実装では要らないはず。
  "waitR" -> "end" := {
  } realTimeout 1000
}

class User extends Model {
  import Const._
  var alertCount = 0
  var meters: Meter = _

  def someMeterBroken : Boolean = {
    meters.hasInstanceInStates(List("broken", "end"))
  }

  "setup" -> "wait" := {
    launch(new Controller)
    meters = new Meter(meterNum)
    launch(meters)
  }

  "wait" -> "wait" := {
    alertCount += 1
    assert(someMeterBroken)
  } subscribe "c-alert"

  "wait" -> "stop" := {
    assert(alertCount > 0)
  } subscribe "end"
}

class Meter(n: Int = 1) extends Model {
  import Const._
  val watt = 1.0
  val brokenWatt = 1000.0

  setInstanceNum(n)

  "wait" -> "run" := {} timeout (10 * min, 20 * min)

  "run" -> "break?" := {
    publish("m-report", watt.toString)
  } timeout (20 * min) label "regular-report"

  "break?" -> "run":= {} weight 0.9
  "break?" -> "broken" := {} weight 0.1

  "broken" -> "broken" := {
    println("broken meter publishing")
    publish("m-report", brokenWatt.toString)
  } timeout (20 * min) // label "regular-report"

  List("run", "break?", "broken") -> "end":= {} subscribe "end"
}

class Controller extends Model {
  var accumulatedWatt: Double = 0.0
  var wattReportCount: Int = 0

  "init" -> "wait" := {
    println("controller starts")
  }

  "wait" -> "wait" := {
    println("controller: message from m-report: "+ getMessage)
    val arrivedWatt = getMessage.toDouble

    if(wattReportCount > 0) {
      // check whether reported Watt is considered an outlier compared to previous reports
      val average: Double = accumulatedWatt / wattReportCount
      println(s"controller:compare $getMessage with average(= $average)")

      // if(average * 100 < arrivedWatt || arrivedWatt * 100 < average) {
      if(!(average / 100 <= arrivedWatt && arrivedWatt <= 100 * average)) {
        publish("c-alert", "meter may be broken")
        println("controller: published alert")
      }
    }

    // update
    accumulatedWatt = accumulatedWatt + arrivedWatt
    wattReportCount += 1
  } subscribe "m-report"

  List("wait", "set") -> "end" := {} subscribe "end"
}