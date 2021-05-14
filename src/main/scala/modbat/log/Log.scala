package modbat.log

import java.io.PrintStream

object Log {
  val All = 0
  val Debug = 1
  val Fine = 2
  val Info = 3
  val Warning = 4
  val Error = 5
  val None = 10

  private var level = Info
  // errLevel can currently not be set - this can be added later
  // if a use case for changing it exists
  private var errLevel = Warning

  // Using Console.err and Console.out is not appropriate, because
  // if you use debug() in a transition, then debug information goes
  // to terminal even if stdout is redirected, which is very annoying.
  // This is apparently because old Console object has been captured.
  // Using explicit PrintStream can avoid the problem.
  private var out: PrintStream = Console.out
  private var err: PrintStream = Console.err
  def setOut(o: PrintStream): Unit = { out = o }
  def setErr(e: PrintStream): Unit = { err = e }

  def setLevel(level: Int) {
    Log.level = level
  }

  def isLogging(level: Int): Boolean = (this.level <= level)

  def log(msg: String, level: Int) {
    if (isLogging(level)) {
      if (errLevel <= level) {
	err.println(msg)
        err.flush()
      } else {
	out.println(msg)
        out.flush()
      }
    }
  }

  def debug(msg: String) = {
    log(s"[DEBUG] ${Thread.currentThread} " + msg, Debug)
  }

  def fine(msg: String) = {
    log("[FINE] " + msg, Fine)
  }

  def info(msg: String) = {
    log("[INFO] " + msg, Info)
  }

  def warn(msg: String) = {
    log("[WARNING] " + msg, Warning)
  }

  def error(msg: String) = {
    log("[ERROR] " + msg, Error)
  }
}
