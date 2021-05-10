package Test

import modbat.dsl._

import accsched.AccSched
import accsched.ASLog.debug

class GuardTest extends Model {
    var cond = true
    var i = 3
    "S1" -> "S1" := {println("S1 -> S1"); i -= 1; if (i == 0) cond = false} guard(cond)
    "S1" -> "S2" := {println("S1 -> S2")} guard(!cond)
    "S2" -> "dummy" := skip guard(false)
}