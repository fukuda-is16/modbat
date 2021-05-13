package test
import modbat.dsl._
import scala.concurrent.duration._

import modbat.mbt.MBT

object Spot {
    val spotNum = 6 // # of spots excluding station
    val spots: Array[Spot] = Array.fill(spotNum + 1)(null)
    val base = spots(0)
    //val setSize(i: Int): Unit = spots.

    val distances: Array[Array[Int]] = Array(
        Array(0,4,5,8,10,3,1),
        Array(4,0,3,6,1,7,2),
        Array(5,3,0,2,1,2,3),
        Array(8,6,2,0,5,1,1),
        Array(10,1,1,5,0,5,2),
        Array(3,7,2,1,5,0,3),
        Array(1,2,3,1,2,3,0)
    )
}

class Spot extends Model {
    val delayMin = 0
    val delayMax = 2

    val time = MBT.time
    var accLoss = 0
    var loss = 0
    var start = 0.millis
    val unit = 5 * 1000 // for each 5 minutes loss may increase
    val inc = 10 // amount of loss increase
    var sprayed = true
    // transitions
    "init" -> "normal" := {
        accLoss = 0
        loss = 0
        start = time.elapsed
    }
    "normal" -> "branch" := {
    } timeout unit
    "branch" -> "normal" := {
    } weight 9
    "branch" -> "normal" := {
        //require(loss == 0)
        if (loss == 0) {
            println("incremented loss")
            loss += inc
            sprayed = false
            start = time.elapsed
        }
    } weight 1

    // methods

    val accLossLimit = 1000
    val delayLimit = 650
    def cure(m: Int): Unit = {
        println(s"cure $m")
        accLoss += loss * (time.elapsed - start).toMillis.toInt
        println("loss assert")
        assert(accLoss < accLossLimit)
        println("ok")
        if (!sprayed) {
            println("time assert")
            assert((time.elapsed - start).toMillis.toInt < delayLimit)
            println("ok")
            sprayed = true
        }
        loss = 0 max (loss - m)
    }
}