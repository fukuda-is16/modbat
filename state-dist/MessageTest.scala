import modbat.dsl._
import scala.util.Random

class MessageTest extends Model {
  var topic = "testTopic"
  var msg = "testMessage"

  setInstanceNum(2)
  "init" -> "sender" := {
    println("gotoSender")
  } label "gotoSender"

  "init" -> "reciever" := {
    println("gotoReciever")
  } label "gotoReciever" 
  
  "sender" -> "end" := {
    println("send")
    publish(topic, msg)
  } label "send"

  "reciever" -> "end" := {
    println("recieve (message = ${getMessage})")
  }  label "recieve" subscribe topic 

  "reciever" -> "end" := {
    println("never-execute-this")
  } label "never-execute-this" timeout 10
}