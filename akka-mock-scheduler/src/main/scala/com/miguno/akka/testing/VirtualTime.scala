package com.miguno.akka.testing

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

class VirtualTime(_realClock: => Long = System.currentTimeMillis) {
  def realClock: Long = _realClock

  /**
    * There's a circular dependency between the states of [[com.miguno.akka.testing.MockScheduler]] and this class,
    * hence we use the same lock for both.
    */
  private[testing] val lock = new Object

  private[this] var elapsedTime: FiniteDuration = 0.millis

  val scheduler = new MockScheduler(this)

  private val minimumAdvanceStep = 1.second / scheduler.maxFrequency

  /**
    * Returns how much "time" has elapsed so far.
    *
    * @return elapsed time
    */
  def elapsed: FiniteDuration = lock synchronized {
    elapsedTime
  }

  /**
    * Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
    *
    * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has completed.
    *
    * @param step
    */
  def advance(step: FiniteDuration): Unit = {
    require(step >= minimumAdvanceStep, s"minimum supported step is $minimumAdvanceStep")
    var remainder = step
    lock synchronized {
      while(remainder > 0.millis) {
        scheduler.timeUntilNextTask match {
          case Some(d) if d <= remainder => {
            elapsedTime += d; remainder -= d
            scheduler.tick()
          }
          case _ => elapsedTime += remainder; remainder = 0.millis
        }
      }
    }
  }

  /**
    * Advances the time by the requested step, which is similar to [[Thread.sleep( )]].
    *
    * This method invokes [[MockScheduler.tick( )]], and any subsequent `advance`s will wait until the tick has completed.
    *
    * @param millis step in milliseconds
    */
  def advance(millis: Long): Unit = advance(FiniteDuration(millis, TimeUnit.MILLISECONDS))

  override def toString: String = s"${getClass.getSimpleName}(${elapsed.toMillis})"

  def reset(): Unit = {
    elapsedTime = 0.millis
    scheduler.clear()
  }

}
