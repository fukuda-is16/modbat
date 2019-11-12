import modbat.dsl._
import scala.util.Random

class MessageTest extends Model {
  var topic = "testTopic"
  var msg = "testMessage"

  setInstanceNum(2)
  "init" -> "sender" := {
    println("gotoSender")
  } label "gotoSender"

  "init" -> "receiver" := {
    println("gotoReceiver")
  } label "gotoReceiver" 
  
  "sender" -> "end" := {
    println("send")
    publish(topic, msg)
  } label "send"

  "receiver" -> "end" := {
    println("receive (message = ${getMessage})")
  }  label "receive" subscribe topic 

  "receiver" -> "end2" := {
    println("never-execute-this")
  } label "never-execute-this" realTimeout 5000
}