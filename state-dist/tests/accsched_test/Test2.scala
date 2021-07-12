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
        (101, 1 * min, 0),
        (2, 2 * min, 0),
        (102, 2 * min, 0),
        (3, 3 * min, 0),
        (103, 3 * min, 0),
        (4, 4 * min, 0),
        (104, 4 * min, 0),
        (5, 5 * min, 0),
        (105, 5 * min, 0)
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

class Test2 extends Model {
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
    "S1" -> "S2" := {println("s1 -> s2"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S2" -> "S3" := {println("s2 -> s3"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S3" -> "S4" := {println("s3 -> s4"); publish("a", i.toString); i+=1} timeout(1 * min)
    "S4" -> "S5" := {println("s4 -> s5"); publish("a", i.toString); i+=1} timeout(1 * min)
}

class M2 extends Model {
    import Glbl._

    var i = 0
    "T1" -> "T1_mid" := {println("t1 -> t1_mid"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T1_mid" -> "T2" := {println("t1_mid -> t2"); rec(i + 100); observe()}
    "T2" -> "T2_mid" := {println("t2 -> t2_mid"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T2_mid" -> "T3" := {println("t2_mid -> t3"); rec(i + 100); observe()}
    "T3" -> "T3_mid" := {println("t3 -> t3_mid"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T3_mid" -> "T4" := {println("t3_mid -> t4"); rec(i + 100); observe()}
    "T4" -> "T4_mid" := {println("t4 -> t4_mid"); i = getMessage.toInt; rec(i); observe()} subscribe("a")
    "T4_mid" -> "T5" := {println("t4_mid -> t5"); rec(i + 100); observe()}
}