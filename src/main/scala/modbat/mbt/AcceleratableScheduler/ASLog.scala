package accsched

import java.io.PrintStream

object ASLog {
  val Debug = 1
  val Info = 3

  private var level: Int = Debug // Info
  private var out: PrintStream = Console.out

  def setLevel(l: Int) { level = l }
  def setOut(o: PrintStream) { out = o }

  def isLogging(lev: Int): Boolean = {
    return this.level <= lev
  }

  /*
  def levelLabel(lev: Int): String = {
    lev match {
      case Debug => "[DEBUG] "
      case Info => "[INFO] "
      case _ => ""
    }
  }
   */

  def log(msg: => String, lev: Int) {
    if (! isLogging(lev)) { return }
    // out.print(levelLabel(lev))
    if (isLogging(Debug)) {
      out.print(AccSched.getCurrentVirtualTime() + " " + Thread.currentThread + " ")
    }
    out.println(msg)
    out.flush()
  }

  def debug(msg: => String) {
    log(msg, Debug)
  }

  def info(msg: => String) {
    log(msg, Info)
  }
}
