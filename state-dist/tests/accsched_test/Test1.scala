package Test

import modbat.dsl._

import accsched.AccSched
import accsched.ASLog.debug

object Glbl {
    val sec = 1000
    val min = 60 * sec
    var startTime: Long = -1
    val epsilon: Long = 200
    def setStartTime(): Unit = {
        startTime = AccSched.getCurrentVirtualTime
    }
    var idx = 0
    val scenario: Array[(Int, Long, Long)] = Array(
        (1, 1 * min, 0),
        (2, 2 * min, 0),
        (3, 3 * min, 0),
        (4, 4 * min, 0),
        (5, 5 * min, 0)
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

class Test1 extends Model {
    import Glbl._

    "init" -> "done" := {
        setStartTime()
        launch(new M1)
        launch(new M2)
        println("init done")
    }
}

class M1 extends Model {
    import Glbl._

    var i = 1

    // "S1" -> "S1" := {
    //     publish("a", i.toString)
    //     i += 1
    //     if (i == 5) this.
    // } timeout(1 * min) weight(1)
    // "S1" -> "S2" := {

    // } weight(0)
    "S1" -> "S2" := {println("S1 -> S2"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S2" -> "S3" := {println("S2 -> S3"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S3" -> "S4" := {println("S3 -> S4"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S4" -> "S5" := {println("S4 -> S5"); publish("a", i.toString); i+=1} timeout(1 * min)
}

class M2 extends Model {
    import Glbl._

    var i = 1
    "T1" -> "T2" := {println("T1 -> T2"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T2" -> "T3" := {println("T2 -> T3"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T3" -> "T4" := {println("T3 -> T4"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T4" -> "T5" := {println("T4 -> T5"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
}