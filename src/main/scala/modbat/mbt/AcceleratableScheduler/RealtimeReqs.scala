package accsched

// This object should only be touched by AccSched
// FIXME: Enforce this using privacy

class RealtimeReqs {
  var tokenSeq: Int = 0
  val status = scala.collection.mutable.Map[Int, Boolean]()

  def newToken(initVal: Boolean = true): Int = {
    tokenSeq += 1
    status += tokenSeq -> initVal
    return tokenSeq
  }

  def setValue(token: Int, value: Boolean): Unit = {
    status(token) = value
  }

  def discard(token: Int): Unit = {
    status -= token
  }

  def someEnabled: Boolean = {
    for ((_, st) <- status) {
      if (st) { return true }
    }
    return false
  }

  def isEmpty: Boolean = { status.isEmpty }

  // for debug
  override def toString(): String = {
    var ret = "RealtimeReqs("
    for ((token, st) <- status) {
      val ch = if (st) { "R" } else { "." }
      ret += token + ":" + ch + " "
    }
    ret += ")"
    return ret
  }

}
