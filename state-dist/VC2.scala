import modbat.dsl._

object Const {
  val meterNum = 100

  val sec = 1000
  val min = 60 * sec
  val hour = 60 * min

  // var end: Boolean = false
}

class VC2 extends Model {
  import Const._

  "init" -> "run" := {
    // end = false
    val user = new User()
    launch(user)
  }
  
  var count = 0
  "run1" -> "waitR" := {
    publish("end","endMessage")
    // end = true
  } guard(count >= 100)
  
  "run1" -> "run" := {
    count += 1
  } guard(count < 100)

  "run" -> "run1" := {
    showDist()
  } timeout(15 * min)

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
    // assert(someMeterBroken)
  } subscribe "c-alert"

  "wait" -> "stop" := {
    println("aaaaaa")
    showDist()
    assert(alertCount > 0)
  } subscribe "end"
}

class Meter(n: Int) extends Model {
  import Const._
  val watt = 1.0
  val brokenWatt = 10000.0

  setInstanceNum(n)

  "init" -> "wait" := println("init meters")
  "wait" -> "run_0" := {println("meter starts")} timeout (10 * min, 20 * min)

  for(watt <- 0 until 100 by 10) {
    val sfx = s"_${watt}"
    val run = "run" + sfx
    val break = "break?" + sfx

    run -> break := {
      println("normal meter publishing" + sfx)
      publish("m-report", watt.toString)
    } timeout (20 * min) label ("regular-report" + sfx)

    for(i <- 0 until 2) {
      val next_watt = watt - 10 + i * 20
      if (0 <= next_watt && next_watt < 100) break -> s"run_${next_watt}" := {}// guard(!end)
      if (next_watt == 100) break -> "broken" := {}// guard(!end)
    }

    // break -> "end" := {} guard(end)

    run -> "end" := {} subscribe "end"
  }


  // "run" -> "break?" := {
  //   println("normal meter publishing")
  //   publish("m-report", watt.toString)
  // } timeout (20 * min) label "regular-report"

  // "break?" -> "run":= {} weight 0.9
  // "break?" -> "broken" := {} weight 0.1

  "broken" -> "broken" := {
    println("broken meter publishing")
    publish("m-report", brokenWatt.toString)
  } timeout (20 * min) // label "regular-report"

  // "broken_2" -> "broken" := {} guard(!end)
  // "broken_2" -> "end" := {} guard(end)

  // List("run", "break?", "broken") -> "end":= {} subscribe "end"
  "broken" -> "end":= {} subscribe "end"
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
      if(!(average -70 <= arrivedWatt && arrivedWatt <= average + 70)) {
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