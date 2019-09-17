import modbat.dsl._


class FirstTest extends Model {
  "init" -> "end" := {println("hoge\n")} label "end" subscribe "hogeTopic" timeout (100,200)
}
