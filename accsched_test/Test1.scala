package Test
import scala.util.Random

import modbat.dsl._

class Test1 extends Model {
    "init" -> "done" := {
        val m1 = new M1
        launch(m1)
        val m2 = new M2
        launch(m2)
        println("init done")
    }
}

class M1 extends Model {
    val sec = 1000
    val min = 60 * sec

    var i = 1
    "S1" -> "S2" := {println("s1 -> s2"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S2" -> "S3" := {println("s2 -> s3"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S3" -> "S4" := {println("s3 -> s4"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S4" -> "S5" := {println("s4 -> s5"); publish("a", i.toString); i+=1} timeout(1 * min)
}

class M2 extends Model {
    val sec = 1000
    val min = 60 * sec

    var i = 1
    "T1" -> "T2" := {println("t1 -> t2"); val i_rcv = getMessage.toInt; assert(i_rcv == i); i+=1} subscribe("a")
    "T2" -> "T3" := {println("t2 -> t3"); val i_rcv = getMessage.toInt; assert(i_rcv == i); i+=1} subscribe("a")
    "T3" -> "T4" := {println("t3 -> t4"); val i_rcv = getMessage.toInt; assert(i_rcv == i); i+=1} subscribe("a")
    "T4" -> "T5" := {println("t4 -> t5"); val i_rcv = getMessage.toInt; assert(i_rcv == i); i+=1} subscribe("a")
}