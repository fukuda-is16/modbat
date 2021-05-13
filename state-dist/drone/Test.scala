package test
import modbat.dsl._

// import modbat.testlib._
import accsched.{ASThread, AccSched}

object X {
    var vstartTime: Long = 0
    var rstartTime: Long = 0
}

class Test extends Model {
    val droneNum = 3
    val controllerThread = new Controller(droneNum)

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
        // start controller thread
        X.vstartTime = AccSched.getCurrentVirtualTime
        X.rstartTime = System.currentTimeMillis()
        controllerThread.start()
    }
    // "launched" -> "end" := {
    //     publish("")
    // } timeout(1000)
}