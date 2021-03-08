package test
import modbat.dsl._

import modbat.testlib._

class Test extends Model {
    val droneNum = 3
    "init" -> "launched" := {
        // sets up spots and launch spot models except station's one
        // station
        Spot.spots(0) = new Spot
        // other spots
        for(i <- 1 to Spot.spotNum) {
            val s = new Spot
            Spot.spots(i) = s
            launch(s)
        }
        println("launched spots")
        for(i <- 0 until droneNum) {
            launch(new Drone(i))
        }
        // 
        println("launched drones")
        val c = new Controller(droneNum)
        val ct = new MBTThread(c)
        ct.start()
    }
}