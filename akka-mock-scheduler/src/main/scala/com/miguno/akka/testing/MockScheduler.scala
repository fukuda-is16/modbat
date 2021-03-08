package com.miguno.akka.testing

import akka.actor.{Cancellable, Scheduler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * A scheduler whose `tick` can be triggered manually, which is helpful for testing purposes.
  * Tasks are executed synchronously when `tick` is called.
  *
  * Typically this scheduler is used indirectly via a [[VirtualTime]] instance.
  */
class MockScheduler(time: VirtualTime) extends Scheduler {

  private[this] var id = 0L

  // Tasks are sorted descendingly by execution priority, i.e. head is the largest element and thus executed next.
  private[this] var tasks = new collection.mutable.PriorityQueue[Task]()

  /**
    * Runs any tasks that are due at this point in time.  This includes running recurring tasks multiple times if needed.
    * The execution of tasks happens synchronously in the calling thread.
    *
    * Tasks are executed in order based on their scheduled execution time.  We do not define the execution ordering of
    * tasks that are scheduled for the same time.
    *
    * Implementation detail:  If you are using this scheduler indirectly via a [[VirtualTime]] instance, then this method
    * will be called automatically by the [[VirtualTime]] instance, and you should not manually call it.
    */
  def tick(): Unit = {
    time.lock synchronized {
      while (tasks.nonEmpty && tasks.head.start <= time.elapsed) {
        val head = tasks.dequeue()
        if (head.blockUntil >= 0) {
          val waitTime = head.blockUntil - time.realClock
          if (waitTime > 0) Thread.sleep(waitTime)
        }
        head.runnable.run()
        head.interval match {
          case Some(interval) => tasks += new Task(head.start + interval, head.id, head.runnable, head.interval,
            if (head.blockUntil >= 0) time.realClock + interval.toMillis else -1)
          case None =>
        }
      }
    }
  }

  def scheduleOnceWithRealDelay(delay: FiniteDuration)(f: ⇒ Unit) =
    addToTasks(delay, new Runnable { override def run = f }, None, true)

  def scheduleWithRealDelay(initialDelay: FiniteDuration, interval: FiniteDuration)(f: ⇒ Unit) =
    addToTasks(initialDelay, new Runnable { override def run = f }, Option(interval), true)

  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)
                           (implicit executor: ExecutionContext): Cancellable =
    addToTasks(delay, runnable, None)

  override def schedule(initialDelay: FiniteDuration, interval: FiniteDuration, runnable: Runnable)
                       (implicit executor: ExecutionContext): Cancellable =
    addToTasks(initialDelay, runnable, Option(interval))

  private def addToTasks(delay: FiniteDuration, runnable: Runnable, interval: Option[FiniteDuration], realDelay: Boolean = false): Cancellable =
    time.lock synchronized {
      id += 1
      val startTime = time.elapsed + delay
      val blockUntil: Long = if (realDelay) time.realClock + delay.toMillis else -1
      val task = new Task(startTime, id, runnable, interval, blockUntil)
      tasks += task
      //println(s"registered scheduled task which happens at $startTime")
      MockCancellable(this, task)
    }

  private[testing] def cancelTask(task: Task): Unit = {
    time.lock synchronized {
      tasks = tasks.filterNot { x => x.id == task.id }
    }
  }

  def timeUntilNextTask(): Option[FiniteDuration] = {
    time.lock synchronized {
      if(!tasks.nonEmpty) {
        None
      } else {
        Some(tasks.head.start - time.elapsed)
      }
    }
  }

  /**
    * The maximum frequency is 1000 Hz.
    */
  override val maxFrequency: Double = 1.second / 1.millis

  def clear(): Unit = {
    tasks.clear()
  }
}
