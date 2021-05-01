package accsched
//import accsched.AccSched

object ScenChk { // ScenarioChecker
    val epsilon: Long = 200;   
    var start_time: Long = 0;
    var state: Int = 0;
    var scenario: List[(Int, Long, Long)] = _;   // (state, 仮想時刻, 実時刻)
    var idx = 0;

    def init(scenario_ : List[(Int, Long, Long)]) = { // コンストラクタ
        scenario = scenario_;
        start_time = System.currentTimeMillis();
    }

    def rec(state_ : Int): Unit = {
        println(s"ScenChk::rec: setting state ${state_}")
        state = state_;
    }

    // assertだとどこで失敗したのかわからないかも．backtrace() みたいのはないか?
    var pre_state: Int = 0
    def observe(fnl: Boolean = false): Unit = {
        if (state == pre_state) {
            println(s"ScenChk::observe: same state ${state}")
            return;
        }
        pre_state = state
        if (fnl) {
            println(s"SchenChk::observe Final.  idx=${idx}, expected=${scenario.size}")
            assert(idx == scenario.size);
        }else {
            assert(idx < scenario.size);
            val cur_scen = scenario(idx);
            val cur_vt: Long = AccSched.getCurrentVirtualTime();
            val cur_rt = System.currentTimeMillis();
            println(s"ScenChk::observe state: ${state}; expected: ${cur_scen._1}")
            println(s"cur_vt = ${cur_vt - start_time}, expected: ${cur_scen._2}")
            println(s"cur_rt = ${cur_rt - start_time}, expected: ${cur_scen._3}")
            assert(cur_scen._1 == state);
            assert((cur_vt - start_time - cur_scen._2).abs < epsilon);
            assert((cur_rt - start_time - cur_scen._3).abs < epsilon);
            idx += 1;
        }
    }
}

object UnitTest {
    def doit() = {
        while (!AccSched.finished()) {
            AccSched.taskWait();
            ScenChk.observe();
        }
        ScenChk.observe(true);
    }

    def main(args: Array[String]) = {
        if (false) {
            test1() // 仮想時間
            test2() // 実時間
            test3() // AccSched::init(false)
            test4() // scheduleするタスク
            test5() // cancelSchedule
            test6() // disableSkip / cancelDisableSkip
            // test7 and 8 have been cancelled.
            test9() // ASThread::sleep
            test10() // ASThread::asWait
            // test11 has been cancelled.
            // test12() // notification for AccSched from ASThread
        }else {
            test1()
        }

    }

    // 仮想時間
    def test1() = {
        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 2000);
        AccSched.schedule({ ScenChk.rec(2); }, 1000);

        ScenChk.init(List((2, 1000, 0), (1, 2000, 0)));
        doit();
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

    // Test7 and Test8 have been discarded.

    // ASThread::sleep
    def test9() = {
        ScenChk.init(List((1, 10000, 0), (2, 10500, 500)));

        class AST1 extends ASThread {
            override def run(): Unit = {
                println("1: start sleep 10000")
                ASThread.sleep(10000);
                println("1: slept 10000")
                ScenChk.rec(1);
                ScenChk.observe();
                ASThread.sleep(500, real = true);
                println("1: slept 500")
                ScenChk.rec(2);
                ScenChk.observe();
                AccSched.schedule({}, 200, real = true) // for termination
            }
        };

        AccSched.init();
        val ast1 = new AST1;
        ast1.start();

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
                    println(s"AST1: step 1 lock1=${lock1}")
                    ASThread.asWait(lock1);
                    ScenChk.rec(1);
                    ScenChk.observe();
                }
                lock1.synchronized {
                    println(s"AST1: step 2 lock1=${lock1}")
                    ASThread.asWait(lock1, 10000);
                    ScenChk.rec(2);
                    ScenChk.observe();
                }
                lock1.synchronized {
                    println(s"AST1: step 3 lock1=${lock1}")
                    ASThread.asWait(lock1, 10000);
                    ScenChk.rec(3);
                    ScenChk.observe();
                }
                lock1.synchronized {
                    println(s"AST1: step 4 lock1=${lock1}")
                    ASThread.asWait(lock1, 500, real = true);
                    ScenChk.rec(4);
                    ScenChk.observe();
                }
                AccSched.schedule({}, 500, real = true);
            }
        };

        class AST2 extends ASThread {
            override def run(): Unit = {
                ASThread.sleep(1000);
                lock1.synchronized {
                    println(s"AST2: step A lock1=${lock1}")
                    AccSched.asNotifyAll(lock1);
                }
                ASThread.sleep(1000);
                lock1.synchronized {
                    println(s"AST2: step B lock1=${lock1}")
                    AccSched.asNotifyAll(lock1);
                }
                ASThread.sleep(1000000);
                lock1.synchronized {
                    println(s"AST2: step C lock1=${lock1}")
                    AccSched.asNotifyAll(lock1);
                }
                AccSched.schedule({}, 500, real = true);
            }
        };

        AccSched.init();
        val t1 = new AST1;
        val t2 = new AST2;
        t2.start();
        t1.start();

        doit();
    }


    // notification for AccSched from ASThread
    def test12() = {
        
        class AST1 extends ASThread {
            override def run(): Unit = {
                ASThread.sleep(500);

                Thread.sleep(300); // work simulation
                ScenChk.rec(100);
                AccSched.synchronized {
                    AccSched.notifyAll(); // ordinary version
                }

                Thread.sleep(300); // work simulation
                ScenChk.rec(101);
                AccSched.synchronized {
                    AccSched.notifyAll(); // ordinary version
                }

                ASThread.sleep(8900);
                ScenChk.rec(102);
                AccSched.synchronized {
                    AccSched.notifyAll(); // ordinary version
                }
                AccSched.schedule({}, 200, real = true) // for termination
            }
        };

        AccSched.init();
        val token1 = AccSched.disableSkip(None)
        AccSched.schedule({ ScenChk.rec(1); }, 400);
        AccSched.schedule({ ScenChk.rec(2); }, 600);
        AccSched.schedule({ ScenChk.rec(3); }, 900);
        AccSched.schedule({ ScenChk.rec(4); }, 1200);
        AccSched.schedule({ ScenChk.rec(5); }, 5000);
        AccSched.schedule({ ScenChk.rec(1000); }, 1000000);

        val ast1 = new AST1;
        ast1.start();
        AccSched.cancelDisableSkip(token1)

        ScenChk.init(List((1, 400, 0), (2, 600, 100),
            (100, 800, 300), (3, 900, 400),
            (101, 1100, 600), (4, 1200, 600), (5, 5000, 600),
            (102, 10000, 600), (1000, 1000000, 600)));
        AccSched.synchronized {
            while (!AccSched.finished()) {
                println(s"doit is waiting")
                AccSched.notifyAll();
                AccSched.wait();
                println(s"doit wakes up")
                ScenChk.observe();
            }
            ScenChk.observe(true);
        }
    }
}

