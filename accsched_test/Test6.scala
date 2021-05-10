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

class Test6 extends Model {
    import Glbl._

    "init" -> "done" := {
        setStartTime()
        launch(new M1)
        launch(new M1)
        launch(new M1)
        launch(new M2)
        println("init done")
    }
}


class M1 extends Model {
    import Glbl._

    var cnt = 0
    "S1" -> "S2" := {publish("a", "."); cnt += 1} timeout(60 * min)
    "S2" -> "S1" := skip guard(cnt < 999)
    "S2" -> "S3" := {println("S2 -> S3"); publish("a", "fin")} guard(cnt == 999)
}

class M2 extends Model {
    import Glbl._

    var cnt = 0
    var finCnt = 0
    "T1" -> "T1" := {cnt += 1; if (getMessage == "fin") finCnt += 1} subscribe("a")
    "T1" -> "T2" := {println("T1 -> T2"); assert(finCnt == 3 && cnt == 3000)} timeout(61 * min)
}