import modbat.dsl._

class CounterTest extends Model {
  setInstanceNum(100)
  val counter = new Counter()
  var model : Int = 0
  "init" -> "init" := {
    counter.inc
    model += 1
    printf("inc\n")
  } label "inc"

  "init" -> "init" := {
    counter.dec
    model -= 1
    printf("dec\n")
  } label "dec"

  "init" -> "init" := {
    counter.reset
    model = 0
    printf("reset\n")
  } label "reset"

  "init" -> "init" := {
    assert(counter.get == model)
    printf("get\n")
  } label "get" subscribe ("topic")

  "init" -> "end" := {
    printf("end\n")
  } label "end"
}

class Counter {
  private var n = 0 
  def inc = n += 1
  def dec = n -= 1//if (n>3) n -= 2 else n -= 1//Bug!
  def get = n			
  def reset = n = 0	
}

