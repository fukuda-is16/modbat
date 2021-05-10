package Test

import modbat.dsl._

import accsched.AccSched
import accsched.ASLog.debug

class WeightTest2 extends Model {

    instanceNum = 10000
    var cnt1 = 0
    var cnt2 = 0
    var totCnt = 0

    "init" -> "S1" := {
        launch(new Observer)
        println("init done")
    }
    "S1" -> "end" := {println("(1) S1 -> end"); publish("a", 1.toString); println("publish done")} weight(1)
    "S1" -> "end" := {println("(2) S1 -> end"); publish("a", 2.toString); println("publish done")} weight(2)
    "S1" -> "end" := {println("(3) S1 -> end"); publish("a", 3.toString); println("publish done")} weight(3)
    "end" -> "exit" := {println("end -> exit")}
}

class Observer extends Model {
    val cnt = Array.fill(3){0}
    "S1" -> "S1" := {
        val i = getMessage.toInt
        cnt(i - 1) += 1
    } subscribe("a")
    
    "S1" -> "end" := {
        println(s"total: ${cnt.sum}")
        for(i <- 0 until 3) {
            println(s"type ${i + 1}: ${cnt(i)}")
        }
    } timeout(10000000)
}