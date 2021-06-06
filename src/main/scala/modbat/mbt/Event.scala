package modbat.mbt

import modbat.dsl.{State, Transition}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Queue

object Event {

  private val queue = Queue[Event]()

  def put(e: Event): Unit = this.synchronized { queue.enqueue(e) }

  def get(): Option[Event] = this.synchronized {
    if (queue.isEmpty) None else Some(queue.dequeue())
  }

  def clear(): Unit = this.synchronized { queue.clear() }

  def processOne(): Boolean = this.synchronized {
    while (! queue.isEmpty) {
      if (queue.dequeue().execute) { return true }
    }
    return false
  }

}

abstract class Event {
  def execute(): Boolean
}

class EventMessage(mbt: MBT, topic: String, message: String) extends Event {

  def execute(): Boolean = {
    var rval = false
    for (state <- MessageHandler.topics(topic)(mbt)) {
      if (state.messageEffect(topic, message)) { rval = true }
    }
    rval
  }

}

class EventTimeout(taskid: Int, t: Transition, n: Int) extends Event {

  def execute(): Boolean = {
    t.origin.timeoutEffect(taskid, t, n)
  }

}
