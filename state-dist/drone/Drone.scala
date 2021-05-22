package test
import modbat.dsl._

import modbat.mbt.MBT
import accsched.AccSched
class Drone(did: Int) extends Model {
    // Spot info
    type SpotID = Int
    val base: SpotID = 0
    var workingPlace: SpotID = 0
    var currentSpot: SpotID = 0
    var nextSpot: SpotID = 0

    // medicine info
    val m_cap = 30
    var m_rem: Int = m_cap

    // this will be used for branching
    var stay: Boolean = true
    // transitions
    //"init" -> "at_spot" := {}
    //"at_spot" -> "dummy" := {
    //    val t = getMessage.split(" ")
    //    cure(t(0).toInt)
    //    var stay = t(1).toInt == 0
    //} subscribe(s"spray $did")

    "at_spot" -> "branch" := {
        val t = getMessage.split(" ")
        cure(t(0).toInt)
        stay = t(1).toBoolean
        // nextSpot = 0
        // println("come back to base")
    } subscribe(s"spray $did")
    
    "branch" -> "at_spot" := {println(s"drone $did: stay there")} guard(!stay)
    "branch" -> "moving" := {
        nextSpot = 0
        println(s"drone $did: come back to base")
    } guard(stay)

    "at_spot" -> "moving" := {
        nextSpot = toSpotID(getMessage)
        println(s"drone $did, ${AccSched.getCurrentVirtualTime - X.vstartTime}, ${System.currentTimeMillis() - X.rstartTime}: head for $nextSpot , distance = ${distance(currentSpot, nextSpot)}")
        //println(s"$distance(currentSpot, nextSpot")
    } subscribe(s"moveTo $did")

    "moving" -> "arrived" := {
        println(s"drone $did arrived at ${AccSched.getCurrentVirtualTime - X.vstartTime}, ${System.currentTimeMillis() - X.rstartTime}: $currentSpot -> $nextSpot")
        currentSpot = nextSpot
        // update delay
        sendDelayMin = Spot.spots(currentSpot).delayMin
        sendDelayMax = Spot.spots(currentSpot).delayMax
        rcvDelayMin = Spot.spots(currentSpot).delayMin
        rcvDelayMax = Spot.spots(currentSpot).delayMax
    } timeout distance(currentSpot, nextSpot)

    "arrived" -> "at_spot" := {
        //require(currentSpot == base)
        if (currentSpot == base) {
            m_rem = m_cap
            println(s"drone $did, ${AccSched.getCurrentVirtualTime - X.vstartTime}, ${System.currentTimeMillis() - X.rstartTime}: arrived at base")
            report()
        } else {
            //require(currentSpot != base)
            workingPlace = currentSpot
            println(s"drone $did, ${AccSched.getCurrentVirtualTime - X.vstartTime}, ${System.currentTimeMillis() - X.rstartTime}: report loss at current point")
            report()
        }
    }

    // method definitions
    def report(): Unit = {
        val s = Spot.spots(currentSpot)
        publish(s"report $did", s.loss.toString)
        println(s"drone $did reported loss ${s.loss} at Spot ${currentSpot}")
    }
    def cure(amount: Int): Unit = {
        val s = Spot.spots(currentSpot)
        s.cure(amount)
    }
    def toSpotID(s: String): SpotID = s.toInt

    def distance(spot: SpotID, nextSpot: SpotID) = Spot.distances(spot)(nextSpot) * 1000
}