import modbat.dsl._

import modbat.mbt.mqtt_utils.client._
import modbat.mbt.mqtt_utils.broker._

class DelayTest extends Model {
    var stime: Long = 0
    "init" -> "waiting_main" := {
        stime = System.currentTimeMillis
        println(s"init time: ${stime}")
        launch(new Observer)
        launch(new Device)
    }
    "waiting_main" -> "end_main" := {
        val etime = System.currentTimeMillis
        println(s"end time: ${etime}")
        println(s"elapsed time: ${etime - stime}")
        publish("end", "")
        0
    } timeout 3000
}

class Observer extends Model {
    sendDelayMin = 0
    sendDelayMax = 0
    rcvDelayMin = 999
    rcvDelayMax = 999
    "waiting_obs"  -> "published" := {
        publish("req", "")
        println("published request")
    } timeout 1000
    "published" -> "waiting_obs" := {
        println("response came back")
    } subscribe "ack"
    "published" -> "broken" := {
        println("catching ack failed")
    } timeout 1000
    
    "waiting_obs" -> "end_obs" := {
        println("Observer ended")
    } subscribe "end"
    "published" -> "end_obs" := {
        println("Observer ended")
    } subscribe "end"
    "broken" -> "end_obs" := {
        println("failed")
        assert(false)
    } subscribe "end"
}

class Device extends Model {
    "waiting_dev" -> "waiting_dev" := {
        publish("ack", "")
        println("responsed against a request")
    } subscribe "req"
    "waiting_dev" -> "end_dev" := {
        println("device shut down")
    } subscribe "end"
}