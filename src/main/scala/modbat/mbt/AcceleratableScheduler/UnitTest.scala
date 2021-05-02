package accsched
//import accsched.AccSched
import scala.util.Random

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
        AccSched.taskNotify()
    }

    var pre_state: Int = 0
    def observe(fnl: Boolean = false): Unit = {
        if (state == pre_state) {
            println(s"ScenChk::observe: same state ${state}")
        }
        else {
            pre_state = state
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
        if (fnl) {
            println(s"SchenChk::observe Final.  idx=${idx}, expected=${scenario.size}")
            assert(idx == scenario.size);
        }
    }
}

object UnitTest {
    def doit() = {
        while (AccSched.taskWait()) {
            println("before observe")
            ScenChk.observe();
            println("after observe")
        }
        println("before final observe")
        ScenChk.observe(true);
    }

    def main(args: Array[String]) = {
        if (false) {
            test1() // 仮想時間
            test2() // 実時間
            /*
            test3() // AccSched::init(false)
            test4() // scheduleするタスク
            test5() // cancelSchedule
            test6() // disableSkip / cancelDisableSkip
            test7() // realtime schedule/cancelSchedule in tasks
            // test8 has been cancelled.
            test9() // ASThread::sleep
            test10() // ASThread::asWait
            // test11 has been cancelled.
            test12() // message sending simulation
             */
        }else {
            if (args(0) == "test1") { test1() }
            else if (args(0) == "test2") { test2() }
            else if (args(0) == "test3") { test3() }
            else if (args(0) == "test4") { test4() }
            else if (args(0) == "test5") { test5() }
            else if (args(0) == "test6") { test6() }
            else if (args(0) == "test7") { test7() }
            else if (args(0) == "test9") { test9() }
            else if (args(0) == "test10") { test10() }
            else if (args(0) == "test12") { test12() }
        }

    }

    // 仮想時間
    def test1() = {
        println("=== test1 ===")
        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 2000);
        AccSched.schedule({ ScenChk.rec(2); }, 1000);

        ScenChk.init(List((2, 1000, 0), (1, 2000, 0)));
        doit();
    }

    // 実時間
    def test2() = {
        println("=== test2 ===")
        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 2000, real = true);
        AccSched.schedule({ ScenChk.rec(2); }, 1000, real = true);

        ScenChk.init(List((2, 1000, 1000), (1, 2000, 2000)));
        doit();
    }

    // AccSched.init(false)
    def test3() = {
        println("=== test3 ===")
        AccSched.init(false);
        AccSched.schedule({ ScenChk.rec(1); }, 2000);
        AccSched.schedule({ ScenChk.rec(2); }, 1000);

        ScenChk.init(List((2, 1000, 1000), (1, 2000, 2000)));
        doit();
    }

    // scheduleするタスク
    def test4() {
        println("=== test4 ===")
        AccSched.init();
        AccSched.schedule({ ScenChk.rec(1); }, 1000);
        AccSched.schedule({
            ScenChk.rec(2);
            AccSched.schedule({ ScenChk.rec(3); }, 1000);
            AccSched.schedule({ ScenChk.rec(5); }, 3000);
        }, 2000);
        AccSched.schedule({ ScenChk.rec(4); }, 4000);

        ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (3, 3000, 0),
            (4, 4000, 0), (5, 5000, 0)));
        doit();
    }

    // cancelSchedule
    def test5() = {
        println("=== test5 ===")
        AccSched.init();
        val task1 = AccSched.schedule({ ScenChk.rec(1); }, 1000);
        val task2 = AccSched.schedule({ ScenChk.rec(2); }, 2000);
        val task3 = AccSched.schedule({ ScenChk.rec(3); }, 3000);
        AccSched.cancelSchedule(task2);
        val task4 = AccSched.schedule({ ScenChk.rec(4); }, 4000);
        AccSched.cancelSchedule(task3);
        val task5 = AccSched.schedule({ ScenChk.rec(5); }, 5000);
        AccSched.cancelSchedule(task1);

        ScenChk.init(List((4, 4000, 0), (5, 5000, 0)));
        doit();
    }

    // askRealtime / cancelRealtime
    def test6() = {
        println("=== test6 ===")
        AccSched.init()
        val task1 = AccSched.schedule({ ScenChk.rec(1) }, 1000)
        val task2 = AccSched.schedule({ ScenChk.rec(2) }, 2000)
        val task3 = AccSched.schedule({ ScenChk.rec(3) }, 3000)
        val task4 = AccSched.schedule({ ScenChk.rec(4) }, 4000)
        var token: Int = -1
        val task5 = AccSched.schedule({
            ScenChk.rec(5)
            token = AccSched.getToken()
        }, 1500)
        val task6 = AccSched.schedule({
            ScenChk.rec(6)
            AccSched.askRealtime(token)
        }, 2500)
        val task7 = AccSched.schedule({
            ScenChk.rec(7)
            AccSched.cancelRealtime(token)
        }, 3500)
        val task8 = AccSched.schedule({
            ScenChk.rec(8)
            AccSched.discardToken(token)
        }, 4500)

        ScenChk.init(List((1, 1000, 0), (5, 1500, 0), (2, 2000, 0),
            (6, 2500, 0),
            (3, 3000, 500), (7, 3500, 1000), (4, 4000, 1000), (8, 4500, 1000)))
        doit();
    }


    // realtime schedule/cancelSchedule in tasks
    def test7() {
        println("=== test7 ===")
        var taskID1 = -1
        AccSched.init()
        AccSched.schedule({ ScenChk.rec(1); }, 1000)
        AccSched.schedule({
            ScenChk.rec(2)
            AccSched.schedule({ ScenChk.rec(3); }, 500, real = true)
        }, 2000)
        AccSched.schedule({
            ScenChk.rec(4)
            taskID1 = AccSched.schedule({ ScenChk.rec(5); }, 10000, real = true)
        }, 3000)
        AccSched.schedule({
            ScenChk.rec(6)
            AccSched.cancelSchedule(taskID1)
        }, 3500)
        AccSched.schedule({ ScenChk.rec(7); }, 4000)

        ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (3, 2500, 500),
            (4, 3000, 500), (6, 3500, 1000), (7, 4000, 1000)))
        doit()
    }

    // ASThread::sleep
    def test9() = {
        println("=== test9 ===")
        class AST1 extends ASThread {
            override def run(): Unit = {
                println("1: start sleep 10000")
                ASThread.sleep(10000);
                println("1: slept 10000")
                ScenChk.rec(1)
                ASThread.sleep(500, real = true);
                println("1: slept 500")
                ScenChk.rec(2)
                terminate()
            }
        };

        AccSched.init();
        val ast1 = new AST1;
        ast1.start();

        ScenChk.init(List((1, 10000, 0), (2, 10500, 500)));
        doit();
    }


    // ASThread::asWait
    def test10() = {
        println("=== test10 ===")
        val lock1 = new AnyRef;
        class AST1 extends ASThread {
            override def run(): Unit = {
                lock1.synchronized {
                    println(s"AST1: step 1 lock1=${lock1}")
                    ASThread.asWait(lock1);
                    ScenChk.rec(1);
                }
                lock1.synchronized {
                    println(s"AST1: step 2 lock1=${lock1}")
                    ASThread.asWait(lock1, 10000);
                    ScenChk.rec(2);
                }
                lock1.synchronized {
                    println(s"AST1: step 3 lock1=${lock1}")
                    ASThread.asWait(lock1, 10000);
                    ScenChk.rec(3);
                }
                lock1.synchronized {
                    println(s"AST1: step 4 lock1=${lock1}")
                    ASThread.asWait(lock1, 500, real = true);
                    ScenChk.rec(4);
                }
                AccSched.schedule({}, 500, real = true);
                terminate()
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
                terminate()
            }
        };

        AccSched.init();
        val t1 = new AST1;
        val t2 = new AST2;
        println(s"t1.token=${t1.token}, t2.token=${t2.token}")
        t2.start();
        t1.start();

        ScenChk.init(List((1, 1000, 0), (2, 2000, 0), (3, 12000, 0),
            (4, 12500, 500)));
        doit();
    }

    // message sending simulation
    def test12() = {
        println("=== test12 ===")
        
        val mainQue = scala.collection.mutable.Queue[Int]()

        AccSched.init();

        trait Callback {
            def messageArrived(t: Int)
        }

        class Listener(cb: Callback) extends ASThread {
            val callback: Callback = cb
            val msgQue = scala.collection.mutable.Queue[Int]()
            @volatile var forceTerminate = false
            override def run(): Unit = {
                msgQue.synchronized {
                    while (true) {
                        if (msgQue.isEmpty) {
                            if (forceTerminate) { return }
                            ASThread.asWait(msgQue)
                        }else {
                            val t = msgQue.dequeue()
                            callback.messageArrived(t)
                        }
                    }
                }
            }
            def enqueMsg(t: Int): Unit = {
                println(s"enqueMsg: enter for ${t}")
                msgQue.synchronized {
                    println(s"enqueMsg: got lock for ${t}")
                    msgQue += t
                    AccSched.asNotifyAll(msgQue)
                }
            }
            def shutdown(): Unit = {
                forceTerminate = true
                msgQue.synchronized { AccSched.asNotifyAll(msgQue) }
                terminate()
            }

        }

        object MyCallback extends Callback {
            def messageArrived(t: Int) {
                println(s"messageArrived: t = ${t}")
                mainQue.synchronized { mainQue += t }
                AccSched.taskNotify()
            }
        }
        val listener = new Listener(MyCallback)

        object SUT1 extends ASThread {
            override def run(): Unit = {
                ASThread.sleep(1000)
                listener.enqueMsg(1)
                ASThread.sleep(200, real=true)
                listener.enqueMsg(2)
                ASThread.sleep(200)
                listener.enqueMsg(4)
                ASThread.sleep(200, real=true)
                listener.enqueMsg(6)
                terminate()
            }
        };

        object SUT2 extends ASThread {
            override def run(): Unit = {
                ASThread.sleep(1300)
                listener.enqueMsg(3)
                ASThread.sleep(200)
                listener.enqueMsg(5)
                ASThread.sleep(500)
                listener.enqueMsg(7)
                terminate()
            }
        };

        listener.start()
        SUT1.start()
        SUT2.start()

        ScenChk.init(List(
            (1, 1000, 0), (2, 1200, 200), (3, 1300, 200),
            (4, 1400, 200), (5, 1500, 300), (6, 1600, 400),
            (7, 2000, 400)
        ))
        while (AccSched.taskWait()) {
            mainQue.synchronized {
                while (! mainQue.isEmpty) {
                    val t = mainQue.dequeue()
                    ScenChk.rec(t)
                    ScenChk.observe()
                    if (t == 7) {
                        listener.shutdown()
                    }
                }
            }
        }
        ScenChk.observe(true)

    }
}

