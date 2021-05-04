package accsched

import ASLog.debug

class Task(
  time: Long,
  taskID: Int,
  // task: Runnable,
  /*
   Note that action is now not a Runnable but a function.
   AccSched's thread loop should not terminate until all tasks have
   been completed.  At the moment we only support functions.
   TODO: supporting Runnables with the feature
         detect their completions.
   */
  action: => Unit,
  optToken: Option[Int]
) extends Ordered[Task] {

  def getTime(): Long = time
  def getTaskID(): Int = taskID
  def execute(): Unit = {
    debug("Task.execute() just before action")
    action
    debug("Task.execute() just after action")
  }
  def getOptToken(): Option[Int] = optToken

  def compare(o: Task): Int = {
    o.getTime().compare(getTime())
  }
}
