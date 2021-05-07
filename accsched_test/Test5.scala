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
        (0, 2 * min, 0),
        (0, 12 * min, 0),
        (0, 17 * min, 0)
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

class Test5 extends Model {
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

    "S1" -> "S2" := {println("S1 -> S2"); publish("a", ".")} timeout(2 * min)
    "S2" -> "S3" := {println("S2 -> S3"); publish("a", "fin")} timeout(15 * min)
}

class M2 extends Model {
    import Glbl._

    "T1" -> "T1" := {println("T1 -> T1"); observe()} timeout(10 * min)
    "T1" -> "T2" := {println(s"T1 -> T2, reveived ${getMessage}"); observe()} subscribe("a")
    "T2" -> "T2" := {println("T2 -> T2"); observe()} timeout(10 * min)
    "T2" -> "T3" := {println(s"T2 -> T3, received ${getMessage}"); observe(); assert(idx == 3); println("ok")} subscribe("a")
}
