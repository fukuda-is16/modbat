package modbat.mbt

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.RuntimeException
import java.net.URL
import java.util.BitSet
import java.util.Random
import scala.collection.JavaConversions._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.LinkedHashMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.matching.Regex

import modbat.cov.StateCoverage
import modbat.dsl.Action
import modbat.dsl.Init
import modbat.dsl.Shutdown
import modbat.dsl.State
import modbat.dsl.Transition
import modbat.log.Log
import modbat.trace.Backtrack
import modbat.trace.ErrOrdering
import modbat.trace.ExceptionOccurred
import modbat.trace.ExpectedExceptionMissing
import modbat.trace.Ok
import modbat.trace.RecordedState
import modbat.trace.RecordedTransition
import modbat.trace.TransitionResult
import modbat.util.CloneableRandom
import modbat.util.SourceInfo
import modbat.util.FieldUtil

import org.eclipse.paho.client.mqttv3.{MqttClient, MqttMessage}
import com.miguno.akka.testing.VirtualTime

/** Contains code to explore model */
object Modbat {
  object AppState extends Enumeration {
    val AppExplore, AppShutdown = Value
  }
  import AppState._
  val origOut = Console.out
  val origErr = Console.err
  var out: PrintStream = origOut
  var err: PrintStream = origErr
  var logFile: String = _
  var errFile: String = _
  var failed = 0
  var count = 0
  val firstInstance = new LinkedHashMap[String, MBT]()
  var appState = AppExplore // track app state in shutdown handler
  // shutdown handler is registered at time when model exploration starts
  private var executedTransitions = new ListBuffer[RecordedTransition]
  private var randomSeed: Long = 0 // current random seed
  var masterRNG: CloneableRandom = _
  private val timesVisited = new HashMap[RecordedState, Int]
  val testFailures =
    new HashMap[(TransitionResult, String), ListBuffer[Long]]()
  var isUnitTest = true
  //var slept = false
  var executeAll = false

  var currentCheckingThread: modbat.testlib.MBTThread = null
  val cctLock = new AnyRef // this has to be acquired when touching currentCheckingThread

  def init {
    // reset all static variables
    failed = 0
    count = 0
    firstInstance.clear
    appState = AppExplore
    executedTransitions.clear
    timesVisited.clear
    testFailures.clear
    masterRNG = MBT.rng.asInstanceOf[CloneableRandom].clone
    MBT.init
    // call init if needed
    if (Main.config.init) {
      MBT.invokeAnnotatedStaticMethods(classOf[Init], null)
    }
  }

  def shutdown {
    if (Main.config.shutdown) {
      MBT.invokeAnnotatedStaticMethods(classOf[Shutdown], null)
    }
  }

  def showFailure(f: (TransitionResult, String)) = {
    val failureType = f._1
    val failedTrans = f._2
    assert (TransitionResult.isErr(failureType))
    (failureType: @unchecked) match {
      case ExceptionOccurred(e) => e + " at " + failedTrans
      case ExpectedExceptionMissing =>
      "Expected exception did not occur at " + failedTrans
    }
  }

  def showErrors {
    if (testFailures.size < 1) {
      return
    }
    if (testFailures.size == 1) {
      Log.info("One type of test failure:")
    } else {
      Log.info(testFailures.size + " types of test failures:")
    }
    var i = 0
    for (f <- testFailures.keySet.toSeq.sortWith(ErrOrdering.lt)) {
      i += 1
      Log.info(i + ") " + showFailure(f) + ":")
      val rseeds = testFailures(f)
      Log.info("   " + rseeds.map(_.toHexString).mkString(" "))
    }
  }

  def passFailed(b: Boolean) = {
    if (b) {
      "passed"
    } else {
      "failed"
    }
  }

  def warnPrecond(modelInst: MBT, t: Transition, idx: Int) {
    Log.info("Precondition " + (idx + 1) + " always " +
	     passFailed(t.coverage.precond.precondPassed.get(idx)) +
	     " at transition " +
	     ppTrans(new RecordedTransition(modelInst, t)))


  }

  def preconditionCoverage {
    for ((modelName, modelInst) <- firstInstance) {
      for (t <- modelInst.transitions) {
	val diffSet =
	  t.coverage.precond.precondPassed.clone.asInstanceOf[BitSet]
	diffSet.xor(t.coverage.precond.precondFailed)
	var idx = diffSet.nextSetBit(0)

	while (idx != -1) {
	  warnPrecond(modelInst, t, idx)
	  if (t.coverage.precond.precondFailed.get(idx)) {
	    idx = -1
	  } else {
	    idx = diffSet.nextSetBit(idx + 1)
	  }
	}
      }
    }
  }

  def coverage {
    Log.info(count + " tests executed, " + (count - failed) + " ok, " +
	     failed + " failed.")
    if (count == 0) {
      return
    }
    showErrors
    for ((modelName, modelInst) <- firstInstance) {
      val nCoveredStates =
	(modelInst.states.values filter (_.coverage.isCovered)).size
      val nCoveredTrans =
	(modelInst.transitions filter (_.coverage.isCovered)).size
      var modelStr = ""
      if (firstInstance.size != 1) {
	modelStr = modelName + ": "
      }
      val nStates = modelInst.states.size
      val nTrans = modelInst.transitions.size
      Log.info(modelStr + nCoveredStates + " states covered (" +
	       nCoveredStates * 100 / nStates + " % out of " + nStates + "),")
      Log.info(modelStr + nCoveredTrans + " transitions covered (" +
	       nCoveredTrans * 100 / nTrans + " % out of " + nTrans + ").")
    }
    preconditionCoverage
    randomSeed = (masterRNG.z << 32 | masterRNG.w)
    Log.info("Random seed for next test would be: " + randomSeed.toHexString)
    if (Main.config.dotifyCoverage) {
      for ((modelName, modelInst) <- firstInstance) {
	new Dotify(modelInst, modelName + ".dot").dotify(true)
      }
    }
  }

  object ShutdownHandler extends Thread {
    override def run() {
      if (appState == AppExplore) {
        restoreChannels
        Console.println
        coverage
      }
    }
  }

  def restoreChannels {
    restoreChannel(out, origOut, logFile)
    restoreChannel(err, origErr, errFile, true)
  }

  def restoreChannel(ch: PrintStream, orig: PrintStream,
		     filename: String, isErr: Boolean = false) {
    if (Main.config.redirectOut) {
      ch.close()
      val file = new File(filename)
      if ((Main.config.deleteEmptyLog && (file.length == 0)) ||
	  (Main.config.removeLogOnSuccess && !MBT.testHasFailed)) {
    	  if (!file.delete()) {
	        Log.warn("Cannot delete file " + filename)
      	}
      }
      if (isErr) {
      	System.setErr(orig)
      } else {
	      System.setOut(orig)
        Console.print("[2K\r")
      }
    }
  }

  def explore(n: Int) = {
    init
    if (!isUnitTest) {
      Runtime.getRuntime().addShutdownHook(ShutdownHandler)
    }

    runTests(n)
    coverage
    appState = AppShutdown
    shutdown
    Runtime.getRuntime().removeShutdownHook(ShutdownHandler)
    // TODO (issue #27): Replace internal System.exit usage with return code
    0
  }

  def getRandomSeed = {
    val rng = MBT.rng.asInstanceOf[CloneableRandom]
    assert (rng.w <= 0xffffffffL)
    assert (rng.z <= 0xffffffffL)
    rng.z << 32 | rng.w
  }

  def wrapRun = {
    Console.withErr(err) {
      Console.withOut(out) {
	      val model = MBT.launch(null)
	      val result = exploreModel(model)
	      MBT.cleanup()
	      result
      }
    }
  }

  def runTest = {
    MBT.clearLaunchedModels
    MBT.testHasFailed = false
    MessageHandler.clear()
    wrapRun
  }

  def runTests(n: Int) {
    for (i <- 1 to n) {
       MBT.rng = masterRNG.clone
       // advance RNG by one step for each path
       // so each path stays the same even if the length of other paths
       // changes due to small changes in the model or in this tool
       randomSeed = getRandomSeed
       val seed = randomSeed.toHexString
       failed match {
       	case 0 => Console.printf("%8d %16s", i, seed)
 	      case 1 => Console.printf("%8d %16s, one test failed.", i, seed)
 	      case _ => Console.printf("%8d %16s, %d tests failed.",
 				 i, seed, failed)
       }
       logFile = Main.config.logPath + "/" + seed + ".log"
       errFile = Main.config.logPath + "/" + seed + ".err"
       if (Main.config.redirectOut) {
 	      out = new PrintStream(new FileOutputStream(logFile))
     	  System.setOut(out)
 	      err = new PrintStream(new FileOutputStream(errFile), true)
       	System.setErr(err)
       } else {
       	Console.println
       }
       MBT.checkDuplicates = (i == 1)
       val result = runTest
       count = i
       restoreChannels
       if (TransitionResult.isErr(result)) {
 	      failed += 1
       } else {
       	assert (result == Ok())
       }
       masterRNG.nextInt(false) // get one iteration in RNG
       if (TransitionResult.isErr(result) && Main.config.stopOnFailure) {
 	      return
       }
     }
  }

  def showTrans(t: RecordedTransition) = {
    if (t == null) {
      "(transition outside model such as callback)"
    } else {
      t.transition.ppTrans(true)
    }
  }

  def exploreModel(model: MBT): TransitionResult = {
    for(m <- MBT.launchedModels) {
      for((_,st) <- m.states) st.viewTransitions

    }
    Log.debug("--- Exploring model ---")
    timesVisited.clear
    executedTransitions.clear
    timesVisited += ((RecordedState(model, model.initialState), 1))
    for (f <- model.tracedFields.fields) {
      val value = FieldUtil.getValue(f, model.model)
      Log.fine("Trace field " + f.getName + " has initial value " + value)
      model.tracedFields.values(f) = value
    }

    val result = exploreSuccessors

    val retVal = result._1
    val recordedTrans = result._2
    assert (retVal == Ok() || TransitionResult.isErr(retVal))
    MBT.testHasFailed = TransitionResult.isErr(retVal)
    if (TransitionResult.isErr(retVal)) {
      val entry = (retVal, showTrans(recordedTrans))
      val rseeds = testFailures.getOrElseUpdate(entry, new ListBuffer[Long]())
      rseeds += randomSeed
    }
    // TODO: classify errors
    Log.debug("--- Resetting to initial state ---")
    retVal
  }

  def addSuccStates(m: MBT, result: ArrayBuffer[(MBT, State)]) {
    //TODO: print log if !quiet 
    //TODO: support loop detection (as supported in addSuccessors)
    for(s <- m.successorStates) result += Tuple2(m, s)
  }

  // check whether all mbt threads are blocked
  // if some mbt threads not blocked, wait for threads to get blocked
  private def allThreadsBlocked(): Boolean = {
    import modbat.testlib.MBTThread
    val toCheck = scala.collection.mutable.ArrayBuffer[MBTThread]()
    var allBlocked: Boolean = true;
    // MBTThread object is also used as shared lock among all MBTThreads
    // prepares toCheck array which is to store unblocked threads
    MBTThread.synchronized {
      val toRemove = scala.collection.mutable.ArrayBuffer[MBTThread]()
      for(thd <- MBTThread.threadsToCheck) {
        if (thd.getState == Thread.State.TERMINATED) toRemove += thd
        else if (thd.blocked == false) {
          allBlocked = false
          toCheck.append(thd)
        }
      }
      for(thd <- toRemove) MBTThread.threadsToCheck -= thd
    }
    if (allBlocked) return true
    for(thd <- toCheck) {
      // set current checking thread appropriately before start checking
      cctLock.synchronized {
        currentCheckingThread = thd
        //println(s"updated currentCheckingThread to $currentCheckingThread")
      }
      // start checking and wait if necessary
      var whileEnd = false
      thd.synchronized {
        while(!whileEnd) {
          MessageHandler.mesLock.synchronized {
            if (MessageHandler.arrivedMessages.nonEmpty) return false
          }
          if (thd.getState == Thread.State.TERMINATED) {
            MBTThread.threadsToCheck -= thd
            whileEnd = true
          } else if (thd.blocked) {
            whileEnd = true
          } else {
            thd.wait()
          }
        }
      }
    }
    return false
  }

  def allSuccStates(givenModel: MBT): Array[(MBT, State)] = {
    val result = new ArrayBuffer[(MBT, State)]()

    MessageHandler.mesLock.synchronized {
      //handle messages arrived from topics
      // while(!MessageHandler.arrivedTopic.isEmpty) {
      while(MessageHandler.arrivedMessages.nonEmpty) {
        val (state, topic, message) = MessageHandler.arrivedMessages.dequeue() // (model, topic, message) に変える
        //Log.info(s"found a message '$message' of '$topic'; starts exploring")
        //if subscribed message, 
        //for(topic <- MessageHandler.arrivedTopic) {
        //  Log.debug(s"(allSuccStates) handling message from topic $topic...")
        //if(MessageHandler.topics.contains(topic)) {
        //  for(state <- MessageHandler.topics(topic)) { // modelとtopicからstateを出すようにする
            if(state.instanceNum > 0) {
              Log.debug(s"(allSuccStates) ${state.instanceNum} instance(s) in ${state.toString} are subscribing $topic")
              // state.messageArrived(topic, MessageHandler.messages(topic))
              state.messageArrived(topic, message)
              result += Tuple2(state.model, state)
            }
            else Log.info(s"no instance was waiting for topic $topic at state ${state.toString}")
        //  }
        //} else {
        //  Log.error(s"Subscribing topic $topic, but no transition is waiting for it.")
        //}
        //MessageHandler.arrivedTopic -= topic
        if(!result.isEmpty) {
          executeAll = true
          //MessageHandler.mesLock.notify()
          return result.toArray
        }
        //}
      }
      //for(topic <- MessageHandler.arrivedTopic) {
      //}f
      //MessageHandler.mesLock.notify()
    }//end of mesLock.synchronize 

    if (givenModel == null) {
      for (m <- MBT.launchedModels filterNot (_ isObserver) filter (_.joining == null)) {
        addSuccStates(m, result)
      }
      if (result.isEmpty) {
        if (!allThreadsBlocked()) return allSuccStates(givenModel)
        
        // move time forward
        return MBT.time.scheduler.timeUntilNextTask match {
          case Some(s) => {
            Log.info(s"advances virtual time by $s from ${MBT.time.elapsed}")
            MBT.time.advance(s)
            allSuccStates(givenModel)
          }
          case None => Array.empty
        }
      }
      //return allSuccStates(givenModel)
    } else {
      if (givenModel.joining == null) {
        addSuccStates(givenModel, result)
      }
    }
    result.toArray
  }

  def updateExecHistory(model: MBT,
			localStoredRNGState: CloneableRandom,
			result: (TransitionResult, RecordedTransition),
			updates: List[(Field, Any)]) {
    result match {
      case (Ok(_), successorTrans: RecordedTransition) =>
        successorTrans.updates = updates
        successorTrans.randomTrace =
          MBT.rng.asInstanceOf[CloneableRandom].trace
        successorTrans.debugTrace =
          MBT.rng.asInstanceOf[CloneableRandom].debugTrace
        MBT.rng.asInstanceOf[CloneableRandom].clear
        executedTransitions += successorTrans
        val timesSeen =
          timesVisited.getOrElseUpdate(RecordedState(model,
                      successorTrans.dest), 0)
        timesVisited += ((RecordedState(model, successorTrans.dest),
              timesSeen + 1))
      case (Backtrack, _) =>
        MBT.rng = localStoredRNGState // backtrack RNG state
        // retry with other successor states in next loop iteration
      case (r: TransitionResult, failedTrans: RecordedTransition) =>
        assert(TransitionResult.isErr(r))
        failedTrans.randomTrace =
          MBT.rng.asInstanceOf[CloneableRandom].trace
        failedTrans.debugTrace =
          MBT.rng.asInstanceOf[CloneableRandom].debugTrace
        MBT.rng.asInstanceOf[CloneableRandom].clear
        executedTransitions += failedTrans
    }
  }

  def otherThreadFailed = {
    MBT.synchronized {
      if (MBT.testHasFailed) {
        printTrace(executedTransitions.toList)
        true
      } else {
        false
      }
    }
  }
  def exploreSuccessors: (TransitionResult, RecordedTransition) = {
    var succStates: Array[(MBT, State)] = allSuccStates(null)
    var backtracked = false

    while(!succStates.isEmpty) {
      Log.info("exploreSuccessors")
      if (MBT.rng.nextFloat(false) < Main.config.abortProbability) {
	      Log.debug("Aborting...")
	      return (Ok(), null)
      }
      if(executeAll) {
        Log.debug("execute all transitions with subscription")
        executeAll = false
      } else {
        val rand = new Random(System.currentTimeMillis())
        val randN = rand.nextInt(succStates.length)
        //val successorState:(MBT, State) = succStates(rand.nextInt(succStates.length))
        succStates = succStates.slice(randN, randN + 1)
      }
      for(successorState <- succStates) {
        val model = successorState._1
        val state = successorState._2
        val fI:Map[modbat.dsl.Transition, Int] = state.feasibleInstances
        state.cancelFeasibleInstances
        for(ins <- fI) {
          val trans: Transition = ins._1
          val n: Int = ins._2
          val result = model.executeTransitionRepeat(trans, n)
          if(TransitionResult.isErr(result._1)) {
            printTrace(executedTransitions.toList)
            return result
          }
        }
      }
      succStates = allSuccStates(null)
    }
  
    Log.debug("No more successors.")
    //slept = false
    Transition.pendingTransitions.clear
    // in case a newly constructed model was never launched
    //TODO: ここをassertion handleするように治す 元のModbat.scalaの476行目以降。case ((OK(), ...)) 

    return (Ok(), null)
  }

  def sourceInfo(action: Action, recordedAction: StackTraceElement) = {
    if (recordedAction != null) {
      val fullClsName = recordedAction.getClassName
      val idx = fullClsName.lastIndexOf('.')
      if (idx == -1) {
	recordedAction.getFileName + ":" + recordedAction.getLineNumber
      } else {
	fullClsName.substring(0, idx + 1).replace('.', File.separatorChar) +
	recordedAction.getFileName + ":" + recordedAction.getLineNumber
      }
    } else {
      assert (action.transfunc != null)
      SourceInfo.sourceInfo(action, false)
    }
  }

  def ppTrans(nModels: Int, transName: String, action: Action,
	      recAction: StackTraceElement, modelName: String) = {
    val sourceInfoStr = sourceInfo(action, recAction)
    if (nModels > 1) {
      sourceInfoStr + ": " + modelName + ": " + transName
    } else {
      sourceInfoStr + ": " + transName
    }
  }

  def ppTrans(recTrans: RecordedTransition): String = {
    val transStr =
      ppTrans (MBT.launchedModels.size, recTrans.trans.ppTrans(true),
	       recTrans.transition.action,
	       recTrans.recordedAction, recTrans.model.name)
    if (Main.config.showChoices && recTrans.randomTrace != null &&
	recTrans.randomTrace.length != 0) {
      val choices = recTrans.debugTrace.mkString(", ")
      transStr + "; choices = (" + choices + ")"
    } else {
      transStr
    }
  }

  def printTrace(transitions: List[RecordedTransition]) {
    Log.warn("Error found, model trace:")
    for (t <- transitions) {
      Log.warn(ppTrans(t))
      for (u <- t.updates) {
	      Log.warn("  " + u._1 + " = " + u._2)
      }
    }
  }
}
