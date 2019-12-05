import modbat.dsl._

class TimeoutTest1 extends Model {
    setInstanceNum(100)
    "init" -> "end" := {
      printf("timeout\n")
    } label "time" timeout (1000,2000)

}