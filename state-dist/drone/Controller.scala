package test

import modbat.mbt.mqtt_utils.client._
import modbat.testlib.{MBTThread => Thread}

sealed trait DroneState
case object MovingToSpot extends DroneState
case object MovingToStation extends DroneState
case object Waiting extends DroneState

class Controller(droneNum: Int) extends Runnable {
    // [left, right)
    val droneRange = Array.fill[(Int,Int)](droneNum)((0,0))
    // MQTT client
    val c = new MqttClient("tcp://localhost:1883", MqttClient.generateClientId())
    // current working spot of drones
    val dSpot = Array.fill[Int](droneNum)(0)
    // drone state: heading for dest / going back for recharging / waiting at site
    val dState = Array.fill[DroneState](droneNum)(Waiting)

    // drone capacity
    val mMax = 100
    // amount of medicine currently carried
    val mRem = Array.fill[Int](droneNum)(mMax)

    private def initDroneRange(): Unit = {
        // site ids except the station range between [1, Spot.spotNum]
        // assign each drone to part of the range
        val q = Spot.spotNum / droneNum
        val r = Spot.spotNum % droneNum
        for(did <- 0 until droneNum) {
            val left = did * q + (did min r) + 1
            val right = (did + 1) * q + ((did + 1) min r) + 1
            assert(right - left > 0)
            droneRange(did) = (left, right)
        }
    }

    private def initMQTT(): Unit = {
        val connOpts = new MqttConnectOptions()
        connOpts.setCleanSession(true)
        c.setCallback(new Callback)
        c.connect(connOpts)
        // subscribe messages whose topics are of the form s"report $did", where 'did' is drone_id
        for(did <- 0 until droneNum) c.subscribe(s"report $did", 1)
    }

    def run(): Unit = {
        initDroneRange()
        initMQTT()
        import modbat.log.Log
        for(did <- 0 until droneNum) {
            val loc = droneRange(did)._1
            moveDrone(did, loc)
            println(s"init: drone $did to $loc")
            dSpot(did) = loc
        }
        val span = /*1000 * */60 * 5 // 5 minutes
        while(true) {
            import modbat.mbt.MBT
            import modbat.log.Log
            //Log.info(s"starts waiting ${MBT.time.elapsed}")
            waitFor(span) // sleep is called inside
            //Log.info(s"finished waiting ${MBT.time.elapsed}")
            for(did <- 0 until droneNum) {
                if (dState(did) != Waiting) {
                    Log.info(s"assertion failed for drone ${did} at ${MBT.time.elapsed}")
                    Log.info(s"drone $did was in state ${dState(did)}")
                    //Log.info(MBT.time.elapsed, did, dState(did))
                }
                assert(dState(did) == Waiting)
                val now = dSpot(did)
                val dest = if (now + 1 == droneRange(did)._2) droneRange(did)._1 else now + 1
                moveDrone(did, dest)
                dSpot(did) = dest
            }
        }
    }

    def waitFor(duration: Int): Unit = {
        //MBTThread.sleep(duration)
        Thread.sleep(duration)
    }

    def moveDrone(did: Int, dest: Int): Unit = {
        assert(dState(did) == Waiting)
        dState(did) = if (dest == 0) MovingToStation else MovingToSpot
        sendMessage(s"moveTo $did", dest.toString)
        println(s"moveTo $did", dest.toString)
        println(s"new state of drone $did is ${dState(did)}")
    }

    def sendMessage(topic: String, message: String): Unit = {
        val t = c.getTopic(topic)
        val mes = new MqttMessage(message.getBytes)
        mes.setQos(1)
        t.publish(mes)
    }

    class Callback extends MqttCallback {
        def connectionLost(e: Throwable): Unit = {}
        def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
        def messageArrived(topic: String, message: MqttMessage): Unit = {
            // only 'report' topic messages will come
            println(topic, message)
            val did = topic.split(" ")(1).toInt
            println(s"received message ${message.toString} from drone $did", dState(did))
            if (dState(did) == MovingToSpot) {
                val badness = message.toString.toInt
                if (badness > 0) {
                    val sprayAmount = badness min mRem(did)
                    mRem(did) -= sprayAmount
                    // badness -= sprayAmount
                    if (sprayAmount < badness) {
                        val topic = s"spray and come back $did"
                        val message = s"${sprayAmount.toString}"
                        sendMessage(topic, message)
                        dState(did) = MovingToStation
                    } else {
                        val topic = s"splay and stay $did"
                        val message = s"${sprayAmount.toString}"
                        sendMessage(topic, message)
                        dState(did) = Waiting
                    }
                } else {
                    dState(did) = Waiting
                }
            } else {
                if (dState(did) != MovingToStation) {
                    println(s"assertion failed: drone $did is in state ${dState(did)}")
                }
                assert(dState(did) == MovingToStation)
                mRem(did) = mMax
                sendMessage(s"moveTo $did", dSpot(did).toString)
                dState(did) = MovingToSpot
            }
            println(s"new state of drone $did is ${dState(did)}")
        }
    }
}