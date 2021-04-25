package accsched
case class Task(time: Long, taskID: Int, task: Runnable, optToken: Option[Int]) extends Ordered[Task] {
    def compare(t: Task): Int =
        if (time > t.time) -1
        else if (time < t.time) 1
        else 0
}