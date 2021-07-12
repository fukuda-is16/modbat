package Test

import modbat.dsl._

import accsched.AccSched
import accsched.ASLog.debug

class WeightTest extends Model {

    var cnt1 = 0
    var cnt2 = 0
    var totCnt = 0

    "S1" -> "S2" := {cnt1 += 1; totCnt += 1} weight(1)
    "S1" -> "S3" := {cnt2 += 1; totCnt += 1} weight(2)

    "S2" -> "S4" := skip
    "S3" -> "S4" := skip
    "S4" -> "S1" := skip guard(totCnt < 1000)
    "S4" -> "end" := {println(s"total: $totCnt\n1: $cnt1\n2: $cnt2")} guard(totCnt >= 1000)
}