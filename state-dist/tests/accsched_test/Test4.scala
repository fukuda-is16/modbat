package Test

import modbat.dsl._

import accsched.AccSched
import accsched.ASLog.debug

object Glbl {
    val sec = 1000
    val min = 100
    var startTime: Long = -1
    val epsilon: Long = 200
    def setStartTime(): Unit = {
        startTime = AccSched.getCurrentVirtualTime
    }
    var idx = 0
    val scenario: Array[(Int, Long, Long)] = Array(
        (1, 10 * min, 10 * min),
        (11, 15 * min, 15 * min),
        (2, 20 * min, 20 * min),
        (12, 25 * min, 25 * min),
        (3, 30 * min, 30 * min),
        (13, 35 * min, 35 * min),
        (4, 40 * min, 40 * min),
        (14, 45 * min, 45 * min)
    )
    var state = 0

    def rec(d: Int): Unit = {
        debug(s"ScenChk::rec: setting state ${d}")
        state = d
    }

    def observe(): Unit = {
        assert(idx < scenario.size)
        val (s, vt, rt) = scenario(idx)
        val cur_vt = AccSched.getCurrentVirtualTime
        val cur_rt = System.currentTimeMillis
        debug(s"ScenChk::observe state: ${state}; expected: ${s}")
        debug(s"cur_vt = ${cur_vt - startTime}, expected: ${vt}")
        debug(s"cur_rt = ${cur_rt - startTime}, expected: ${rt}")
        assert(state == s)
        assert((cur_vt - startTime - vt).abs < epsilon)
        assert((cur_rt - startTime - rt).abs < epsilon)
        idx += 1
    }
}

class Test4 extends Model {
    import Glbl._

    "init" -> "done" := {
        setStartTime()
        launch(new M1)
        launch(new M2)
        launch(new M3)
        println("init done")
    }
}

class M1 extends Model {
    import Glbl._

    var i = 1
    "S1_1" -> "S1_2" := {println("s1_1 -> s2_2"); publish("a", i.toString); i+=1} realTimeout(10 * min)
    "S1_2" -> "S1_3" := {println("s1_2 -> s2_3"); publish("a", i.toString); i+=1} realTimeout(10 * min)
    "S1_3" -> "S1_4" := {println("s1_3 -> s2_4"); publish("a", i.toString); i+=1} realTimeout(10 * min)
    "S1_4" -> "S1_5" := {println("s1_4 -> s2_5"); publish("a", i.toString); i+=1} realTimeout(10 * min)
}

class M2 extends Model {
    import Glbl._

    var i = 11
    "init" -> "S2_1" := {} realTimeout(5 * min)
    "S2_1" -> "S2_2" := {println("s2_1 -> s2_2"); publish("b", i.toString); i+=1} realTimeout(10 * min)
    "S2_2" -> "S2_3" := {println("s2_2 -> s2_3"); publish("b", i.toString); i+=1} realTimeout(10 * min)
    "S2_3" -> "S2_4" := {println("s2_3 -> s2_4"); publish("b", i.toString); i+=1} realTimeout(10 * min)
    "S2_4" -> "S2_5" := {println("s2_4 -> s2_5"); publish("b", i.toString); i+=1} realTimeout(10 * min)
}

class M3 extends Model {
    import Glbl._

    var i = 1
    "T1_1" -> "T1_2" := {println("t1_1 -> t1_2"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T1_2" -> "T2_1" := {println("t1_2 -> t2_1"); i = getMessage.toInt; rec(i); observe()} subscribe("b")
    "T2_1" -> "T2_2" := {println("t2_1 -> t2_2"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T2_2" -> "T3_1" := {println("t2_2 -> t3_1"); i = getMessage.toInt; rec(i); observe()} subscribe("b")
    "T3_1" -> "T3_2" := {println("t3_1 -> t3_2"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T3_2" -> "T4_1" := {println("t3_2 -> t4_1"); i = getMessage.toInt; rec(i); observe()} subscribe("b")
    "T4_1" -> "T4_2" := {println("t4_1 -> t4_2"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T4_2" -> "T5_1" := {println("t4_2 -> t5_1"); i = getMessage.toInt; rec(i); observe()} subscribe("b")
}