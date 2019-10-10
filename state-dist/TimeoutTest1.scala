import modbat.dsl._

class TimeoutTest1 extends Model {
    "init" -> "end" := {
      printf("timeout\n")
    } label "time" timeout 1
}