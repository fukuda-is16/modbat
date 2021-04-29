package accsched
//import accsched.AccSched

object ScenChk { // ScenarioChecker
  val epsilon: Long = 100;   // 0.1sec に相当する Long値．以下同様
  var start_time: Long = 0;
  var state: Int = 0;
  var scenario: List[(Int, Long, Long)] = _;   // (state, 仮想時刻, 実時刻)
  var idx = 0;

  def init(scenario_ : List[(Int, Long, Long)]) = { // コンストラクタ
    scenario = scenario_;
    start_time = System.currentTimeMillis();
  }

  def rec(state_ : Int): Unit = {
    state = state_;
  }

  // assertだとどこで失敗したのかわからないかも．backtrace() みたいのはないか?
  var pre_state: Int = 0
  def observe(fnl: Boolean = false): Unit = {
    if (state == pre_state) { return; }
    pre_state = state
    if (fnl) {
      println(idx)
      assert(idx == scenario.size);
    }else {
      assert(idx < scenario.size);
      val cur_scen = scenario(idx);
      val cur_vt: Long = AccSched.getCurrentVirtualTime();
      val cur_rt = System.currentTimeMillis();
      println(s"state: ${state}; expected: ${cur_scen._1}")
      assert(cur_scen._1 == state);
      println(s"cur_vt = ${cur_vt - start_time}, cur_scen._2 = ${cur_scen._2}")
      assert((cur_vt - start_time - cur_scen._2).abs < epsilon);
      println("b")
      assert((cur_rt - start_time - cur_scen._3).abs < epsilon);
      idx += 1;
    }
  }
}

//-------------------------------------------------------------------------------
//def doit() {
//  while (!AccSched.finished()) {
//    AccSched.taskWait();
//    ScenChk.observe();
//  }
//  ScenChk.observe(true);
//}

//-------------------------------------------------------------------------------

// 仮想時間
object UnitTest {
    def doit() = {
        while (!AccSched.finished()) {
            AccSched.taskWait();
            ScenChk.observe();
        }
        ScenChk.observe(true);
    }

    def main(args: Array[String]) = {
        // 仮想時間
        // test1()

        // 実時間
        // test2()

        // AccSched.init(false)
        // test3()

        // scheduleするタスク
        // test4()

        // cancelSchedule
        // test5()

        // disableSkip / cancelDisableSkip
        // test6()

        // asNotifyAll
        // test7()

        // ASThread::sleep
        test9()

        // ASThread::asWait
        // test10()
    }

    // 仮想時間
    def test1() = {
        ScenChk.init(List((2, 1000, 0), (1, 2000, 0)));

        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 2000);
        AccSched.schedule({ ScenChk.rec(2); }, 1000);

        doit();
        //AccSched.schedulerNotify()
    }

    // 実時間
    def test2() = {
        ScenChk.init(List((2, 1000, 1000), (1, 2000, 2000)));

        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); println("rec 1 executed") }, 2000, real = true);
        AccSched.schedule({ ScenChk.rec(2); println("rec 2 executed") }, 1000, real = true);

        doit();
        //AccSched.schedulerNotify()
    }

    // AccSched.init(false)
    def test3() = {
        ScenChk.init(List((2, 1000, 1000), (1, 2000, 2000)));

        AccSched.init(false);
        AccSched.schedule({ ScenChk.rec(1); }, 2000);
        AccSched.schedule({ ScenChk.rec(2); }, 1000);

        doit();
    }

    // scheduleするタスク
    def test4() {
        ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (3, 3000, 0),
                          (4, 4000, 0), (5, 5000, 0)));

        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 1000);
        AccSched.schedule({
          ScenChk.rec(2);
          AccSched.schedule({ ScenChk.rec(3); }, 1000);
          AccSched.schedule({ ScenChk.rec(5); }, 3000);
        }, 2000);
        AccSched.schedule({ ScenChk.rec(4); }, 4000);

        doit();
    }

    // cancelSchedule
    def test5() = {
        ScenChk.init(List((4, 4000, 0), (5, 5000, 0)));

        AccSched.init();
        val task1 = AccSched.schedule({ ScenChk.rec(1); }, 1000);
        val task2 = AccSched.schedule({ ScenChk.rec(2); }, 2000);
        val task3 = AccSched.schedule({ ScenChk.rec(3); }, 3000);
        AccSched.cancelSchedule(task2);
        val task4 = AccSched.schedule({ ScenChk.rec(4); }, 4000);
        AccSched.cancelSchedule(task3);
        val task5 = AccSched.schedule({ ScenChk.rec(5); }, 5000);
        AccSched.cancelSchedule(task1);

        doit();
    }


    // disableSkip / cancelDisableSkip
    def test6() = {
        ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (5, 2500, 0),
                    (3, 3000, 500), (6, 3500, 1000), (4, 4000, 1000)));

        AccSched.init();
        //TimeMeasure.reset();
        val task1 = AccSched.schedule({ ScenChk.rec(1); }, 1000);
        val task2 = AccSched.schedule({ ScenChk.rec(2); }, 2000);
        val task3 = AccSched.schedule({ ScenChk.rec(3); }, 3000);
        val task4 = AccSched.schedule({ ScenChk.rec(4); }, 4000);
        var token: Int = -1111
        val task5 = AccSched.schedule({
            ScenChk.rec(5);
            token = AccSched.disableSkip(None);
        }, 2500);
        val task6 = AccSched.schedule({
            ScenChk.rec(6);
            AccSched.cancelDisableSkip(token);
        }, 3500);

        doit();
    }

    // // asNotifyAll
    // def test7() = {
    //   val lock1 = new AnyRef;
    //   val lock2 = new AnyRef;

    //   ScenChk.init(List((1, 0, 0), (3, 10000, 0), (4, 10000, 0),
    //                 (10, 10000, 0), (20, 20000, 0)));

    //   class T1(lock: AnyRef, st: Int) extends Thread {
    //     override def run(): Unit = {
    //       lock.synchronized {
    //         ScenChk.rec(1);
    //         lock.wait();
    //         ScenChk.rec(st);
    //       }
    //     }
    //   };

    //   class T2 extends Thread {
    //     override def run(): Unit = {
    //       ASThread.sleep(10000);
    //       ScenChk.rec(3);
    //       lock1.synchronized {
    //         AccSched.asNotifyAll(lock1);
    //         ScenChk.rec(4);
    //       }
    //       ASThread.sleep(10000);
    //       lock2.synchronized {
    //         AccSched.asNotifyAll(lock2);
    //         ScenChk.rec(5);
    //       }
    //     }
    //   };
    //   AccSched.init();
    //   val t11a = new T1(lock1, 10);
    //   val t11b = new T1(lock1, 10);
    //   val t12a = new T1(lock2, 20);
    //   val t12b = new T1(lock2, 20);
    //   val t2 = new T2;
    //   t2.start();
    //   t11a.start();
    //   t11b.start();
    //   t12a.start();
    //   t12b.start();

    //   AccSched.schedule({println("wait")}, 9999000)
    //   doit();
    // }


    // ASThread::sleep
    def test9() = {
      ScenChk.init(List((1, 10000, 0), (2, 10500, 500)));

      class AST1 extends ASThread {
        override def run(): Unit = {
          println("1: start sleep 10000")
          ASThread.sleep(10000);
          println("1: slept 10000")
          ScenChk.rec(1);
          ASThread.sleep(500, real = true);
          println("1: slept 500")
          ScenChk.rec(2);
        }
      };

      class T2 extends Thread {
        override def run(): Unit = {
          synchronized {
            println("2: start wait")
            AccSched.asWait(this, 20000);
            println("end wait")
            // ASThread の終了検知をしていない場合には，このタイムアウトの
            // 時点で，ast1 が終了していることを知り，
            // AccSched.finished() が true を返すようになる．
          }
        }
      };

      AccSched.init();
      val ast1 = new AST1;
      val t2 = new T2;
      ast1.start();
      t2.start();

      doit();
    }


    // ASThread::asWait
    def test10() = {
      val lock1 = new AnyRef;
      ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (3, 12000, 0),
                    (4, 12500, 500)));

      class AST1 extends ASThread {
        override def run(): Unit = {
          lock1.synchronized {
            ASThread.asWait(lock1);
            ScenChk.rec(1);
          }
          lock1.synchronized {
            ASThread.asWait(lock1, 10000);
            ScenChk.rec(2);
          }
          lock1.synchronized {
            ASThread.asWait(lock1, 10000);
            ScenChk.rec(3);
          }
          lock1.synchronized {
            ASThread.asWait(lock1, 500, real = true);
            ScenChk.rec(4);
          }
        }
      };

      class T2 extends Thread {
        override def run(): Unit = {
          lock1.synchronized {
            AccSched.asNotifyAll(lock1, 1000);
          }
          lock1.synchronized {
            AccSched.asNotifyAll(lock1, 1000);
          }
          lock1.synchronized {
            AccSched.asNotifyAll(lock1, 1000000);
          }
        }
      };

      doit();
    }
}





/*
// 実時間
{
  ScenChk.init({(2, 1sec, 1sec), (1, 2sec, 2sec)});

  AccSched.init();
  AccSched.schedule({ ScenChk.rec(1); }, 2sec, real = true);
  AccSched.schedule({ ScenChk.rec(2); }, 1sec, real = true);

  doit();
}

// AccSched.init(false)
{
  ScenChk.init({(2, 1sec, 1sec), (1, 2sec, 2sec)});

  AccSched.init(false);
  AccSched.schedule({ ScenChk.rec(1); }, 2sec);
  AccSched.schedule({ ScenChk.rec(2); }, 1sec);

  doit();
}

// scheduleするタスク
{
  ScenChk.init({(1, 1sec, 0sec), (2, 2sec, 0sec), (3, 3sec, 0sec),
                (4, 4sec, 0sec), (5, 5sec, 0sec)});

  AccSched.init();
  AccSched.schedule({ ScenChk.rec(1); }, 1sec);
  AccSched.schedule({
    ScenChk.rec(2);
    AccSched.schedule({ ScenChk.rec(3); }, 1sec);
    AccSched.schedule({ ScenChk.rec(5); }, 3sec);
  }, 2sec);
  AccSched.schedule({ ScenChk.rec(4); }, 4sec);

  doit();
}

// cancelSchedule
{
  ScenChk.init({(4, 4sec, 0sec), (5, 5sec, 0sec)});

  AccSched.init();
  val task1 = AccSched.schedule({ ScenChk.rec(1); }, 1sec);
  val task2 = AccSched.schedule({ ScenChk.rec(2); }, 2sec);
  val task3 = AccSched.schedule({ ScenChk.rec(3); }, 3sec);
  AccSched.cancelSchedule(task2);
  val task4 = AccSched.schedule({ ScenChk.rec(4); }, 4sec);
  AccSched.cancelSchedule(task3);
  val task5 = AccSched.schedule({ ScenChk.rec(5); }, 5sec);
  AccSched.cancelSchedule(task1);

  doit();
}

// disableSkip / cancelDisableSkip
{
  ScenChk.init({(1, 1sec, 0sec), (2, 2sec, 0sec), (5, 2.5sec, 0sec),
              (3, 3sec, 0.5sec), (6, 3.5sec, 1sec), (4, 4sec, 1sec)});

  AccSched.init();
  TimeMeasure.reset();
  val task1 = AccSched.schedule({ ScenChk.rec(1); }, 1sec);
  val task2 = AccSched.schedule({ ScenChk.rec(2); }, 2sec);
  val task3 = AccSched.schedule({ ScenChk.rec(3); }, 3sec);
  val task4 = AccSched.schedule({ ScenChk.rec(4); }, 4sec);
  val task5 = AccSched.schedule({
    ScenChk.rec(5);
    token = AccSched.disableSkip(None);
  }, 2.5sec);
  val task6 = AccSched.schedule({
    ScenChk.rec(6);
    cancelDisableSkip(token);
  }, 3.5sec);

  doit();
}

// asNotifyAll
{
  lock1: AnyRef;
  lock2: AnyRef;

  ScenChk.init({(1, 0sec, 0sec), (3, 10sec, 0sec), (4, 10sec, 0sec),
                (10, 10sec, 0sec), (20, 20sec, 0sec)});

  class T1 extends Thread {
    val lock: AnyRef;
    val st: Int;
    def run(): Unit {
      lock.synchronized {
        ScenChk.rec(1);
        lock.wait();
        ScenChk.rec(st);
      }
    }
    def AST1(lock_: AnyRef, st_: Int) {
      lock = lock_;
      st = st_;
    }

  };

  class T2 extends Thread {
    def run(): Unit {
      ASThread.sleep(10sec);
      ScenChk.rec(3);
      lock1.synchronized {
        AccSched.notifyAll(lock1);
        ScenChk.rec(4);
      }
      ASThread.sleep(10sec);
      lock2.synchronized {
        AccSched.notifyAll(lock2);
        ScenChk.rec(5);
      }
    }
  };

  val t11a: T1(lock1, 10);
  val t11b: T1(lock1, 10);
  val t12a: T1(lock2, 20);
  val t12b: T1(lock2, 20);
  val t2: T2;
  t2.start();
  t11a.start();
  t11b.start();
  t12a.start();
  t12b.start();

  doit();
}


// AccSched::asWait with timeout
{
  lock1: AnyRef;

  ScenChk.init({(1, 0sec, 0sec), (4, 5sec, 0sec), (2, 5sec, 0sec),
                (3, 15sec, 0sec)});

  class T1 extends Thread {
    def run(): Unit {
      lock1.synchronized {
        ScenChk.rec(1);
        ASThread.wait(lock1, 10sec);
        ScenChk.rec(2);
      }
      lock1.synchronized {
        ASThread.wait(lock1, 10sec);
        ScenChk.rec(3);
      }
    }
  };

  class T2 extends Thread {
    def run(): Unit {
      ASThread.sleep(5sec);
      lock1.synchronized {
        AccSched.notifyAll(lock1);
        ScenChk.rec(4);
      }
    }
  };

  val t1: T1;
  val t2: T2;
  t2.start();
  t1.start();

  doit();
}

// ASThread::sleep
{
  ScenChk.init({(1, 10sec, 0sec), (2, 10.5sec, 0.5sec});

  class AST1 extends ASThread {
    def run(): Unit {
      ASThread.sleep(10sec);
      ScenChk.rec(1);
      ASThread.sleep(0.5sec, real = true);
      ScenChk.rec(2);
    }
  };

  class T2 extends Thread {
    def run(): Unit {
      synchronized {
        AccSched.wait(this, 20sec);
        // ASThread の終了検知をしていない場合には，このタイムアウトの
        // 時点で，ast1 が終了していることを知り，
        // AccSched.finished() が true を返すようになる．
      }
    }
  };

  val ast1: AST1;
  val t2: T2;
  ast1.start();
  t2.start();

  doit();
}

// ASThread::asWait
{
  val lock1: AnyRef;
  ScenChk.init({(1, 1sec, 0sec), (2, 2sec, 0sec), (3, 12sec, 0sec)
                (4, 12.5sec, 0.5sec)});

  class AST1 extends ASThread {
    def run(): Unit {
      lock1.synchronized {
        ASThread.wait(lock1);
        ScenChk.rec(1);
      }
      lock.synchronized {
        ASThread.wait(lock1, 10sec);
        ScenChk.rec(2);
      }
      lock.synchronized {
        ASThread.wait(lock1, 10sec);
        ScenChk.rec(3);
      }
      lock.synchronized {
        ASThread.wait(lock1, 0.5sec, real = true);
        ScenChk.rec(4);
      }
    }
  };

  class T2 extends Thread {
    def run(): Unit {
      lock1.synchronized {
        AccSched.notifyAll(lock1, 1sec);
      }
      lock1.synchronized {
        AccSched.notifyAll(lock1, 1sec);
      }
      lock1.synchronized {
        AccSched.notifyAll(lock1, 1000sec);
      }
    }
  };

  doit();
}

// notification for AccSched from Ordinary Thread
{
  ScenChk.init({(1, 0.4sec, 0.0sec), (2, 0.6sec, 0.1sec),
                (100, 0.8sec, 0.3sec), (3, 0.9sec, 0.4sec),
                (102, 1.1sec, 0.6sec), (4, 1.2sec, 0.6sec),
                (1000, 1000.0sec, 0.6sec)});
  
  class T1 extends Thread {
    def run(): Unit {
      synchronized { AccSched.wait(this, 0.5sec); }

      val task1 = AccSched.schedule({ScenChk.rec(101);}, 0.6sec, real = true);
      Thread.sleep(0.3sec); // work simulation
      ScenChk.rec(100);
      AccSched.notifyAll(); // ordinary version
      AccSched.cancelSchedule(task1);

      val task2 = AccSched.schedule({ScenChk.rec(102);}, 0.3sec, real = true);
      Thread.sleep(0.5sec); // work simulation
    }
  };

  AccSched.init();
  AccSched.schedule({ ScenChk.rec(1); }, 0.4sec);
  AccSched.schedule({ ScenChk.rec(2); }, 0.6sec);
  AccSched.schedule({ ScenChk.rec(3); }, 0.9sec);
  AccSched.schedule({ ScenChk.rec(4); }, 1.2sec);
  AccSched.schedule({ ScenChk.rec(1000); }, 1000sec);

  val t1: T1;
  t1.start();

  doit();
}

// notification for AccSched from ASThread
{
  ScenChk.init({(1, 0.4sec, 0.0sec), (2, 0.6sec, 0.1sec),
                (100, 0.8sec, 0.3sec), (3, 0.9sec, 0.4sec),
                (101, 1.1sec, 0.6sec), (4, 1.2sec, 0.6sec),
                (102, 10.0sec, 0.6sec), (1000, 1000.0sec, 0.6sec)});
  
  class AST1 extends ASThread {
    def run(): Unit {
      ASThread.sleep(0.5sec);

      Thread.sleep(0.3sec); // work simulation
      ScenChk.rec(100);
      AccSched.notifyAll(); // ordinary version

      Thread.sleep(0.3sec); // work simulation
      ScenChk.rec(101);
      AccSched.notifyAll(); // ordinary version

      ASThread.sleep(8.9sec);
      ScenChk.rec(102);
      AccSched.notifyAll(); // ordinary version
    }
  };

  AccSched.init();
  AccSched.schedule({ ScenChk.rec(1); }, 0.4sec);
  AccSched.schedule({ ScenChk.rec(2); }, 0.6sec);
  AccSched.schedule({ ScenChk.rec(3); }, 0.9sec);
  AccSched.schedule({ ScenChk.rec(4); }, 1.2sec);
  AccSched.schedule({ ScenChk.rec(5); }, 5.0sec);
  AccSched.schedule({ ScenChk.rec(1000); }, 1000sec);

  val ast1: AST1;
  ast1.start();

  doit();

}

*/