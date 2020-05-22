package com.miguno.akka.testing

import scala.concurrent.duration.FiniteDuration

private[testing] case class Task(start: FiniteDuration, id: Long, runnable: Runnable, interval: Option[FiniteDuration], blockUntil: Long = 0)
  extends Ordered[Task] {

  def compare(t: Task): Int =
    if (start > t.start) -1
    else if (start < t.start) 1
    else if (id > t.id) -1
    else if (id < t.id) 1
    else 0

}
