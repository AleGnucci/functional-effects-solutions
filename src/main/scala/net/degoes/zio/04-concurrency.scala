package net.degoes.zio

import zio._
import zio.clock.Clock
import zio.duration.Duration

object ForkJoin extends App {

  import zio.console._

  val printer =
    putStrLn(".").repeat(Schedule.recurs(10))

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then join the fiber using `Fiber#join`,
   * and finally, print out a message "Joined".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- printer.fork
      _ <- putStrLn("Forked")
      _ <- fiber.join
      _ <- putStrLn("Joined")
    } yield ()).exitCode
}

object ForkInterrupt extends App {

  import zio.console._
  import zio.duration._

  val infinitePrinter =
    putStrLn(".").forever

  /**
   * EXERCISE
   *
   * Using `ZIO#fork`, fork the `printer` into a separate fiber, and then
   * print out a message, "Forked", then using `ZIO.sleep`, sleep for 100
   * milliseconds, then interrupt the fiber using `Fiber#interrupt`, and
   * finally, print out a message "Interrupted".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- infinitePrinter.fork
      _ <- putStrLn("Forked")
      _ <- ZIO.sleep(10.millis)
      _ <- fiber.interrupt
      _ <- putStrLn("Interrupted")
    } yield ()).exitCode
}

object ParallelFib extends App {

  import zio.console._

  /**
   * EXERCISE
   *
   * Rewrite this implementation to compute nth fibonacci number in parallel.
   */
  def fib(n: Int): UIO[BigInt] = {
    def loop(n: Int, original: Int): UIO[BigInt] =
      if (n <= 1) UIO(n)
      else
        UIO.effectSuspendTotal {
          (loop(n - 1, original) zipWithPar loop(n - 2, original)) (_ + _) //used zipWithPar instead of zipWith, but now fib creates an exponential amount of fibers
        }

    loop(n, n)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      _ <- putStrLn("What number of the fibonacci sequence should we calculate?")
      n <- getStrLn.orDie.flatMap(input => ZIO(input.toInt)).eventually
      f <- fib(n)
      _ <- putStrLn(s"fib(${n}) = ${f}")
    } yield ExitCode.success
}

object AlarmAppImproved extends App {

  import zio.console._
  import zio.duration._
  import java.io.IOException
  import java.util.concurrent.TimeUnit

  lazy val getAlarmDuration: ZIO[Console, IOException, Duration] = {
    def parseDuration(input: String): IO[NumberFormatException, Duration] =
      ZIO.effect(Duration((input.toDouble * 1000.0).toLong, TimeUnit.MILLISECONDS))
        .refineToOrDie[NumberFormatException]

    val fallback = putStrLn("You didn't enter a number of seconds!") *> getAlarmDuration //recursive call

    for {
      _ <- putStrLn("Please enter the number of seconds to sleep: ")
      input <- getStrLn
      duration <- parseDuration(input) orElse fallback
    } yield duration
  }

  /**
   * EXERCISE
   *
   * Create a program that asks the user for a number of seconds to sleep,
   * sleeps the specified number of seconds using ZIO.sleep(d), concurrently
   * prints a dot every second that the alarm is sleeping for, and then
   * prints out a wakeup alarm message, like "Time to wakeup!!!".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      duration <- getAlarmDuration
      fiber <- putStrLn(".").repeat(Schedule.fixed(1.seconds)).fork
      _ <- ZIO.sleep(duration)
      _ <- fiber.interrupt
      _ <- putStrLn("Time to wakeup!!!")
    } yield ()).exitCode
}

object ComputePi extends App {

  import zio.random._
  import zio.console._
  import zio.clock._
  import zio.duration._
  import zio.stm._

  /**
   * Some state to keep track of all points inside a circle,
   * and total number of points.
   */
  final case class PiState(inside: Ref[Long], total: Ref[Long])

  /**
   * A function to estimate pi.
   */
  def estimatePi(inside: Long, total: Long): Double =
    (inside.toDouble / total.toDouble) * 4.0

  /**
   * A helper function that determines if a point lies in
   * a circle of 1 radius.
   */
  def insideCircle(x: Double, y: Double): Boolean =
    Math.sqrt(x * x + y * y) <= 1.0 // using pythagorean theorem

  /**
   * An effect that computes a random (x, y) point.
   */
  val randomPoint: ZIO[Random, Nothing, (Double, Double)] =
    nextDouble zip nextDouble

  def calculateForOneTimeStep(pointsForEachStep: Int): ZIO[Random, Nothing, Int] = {
    val initialList = List.fill(pointsForEachStep)(0)
    for {
      points <- ZIO.foreach(initialList)(_ => randomPoint)
      pointsInside = points.count(point => insideCircle(point._1, point._2))
    } yield pointsInside
  }

  def createState(): ZIO[Any, Nothing, PiState] =
    for {
      xState <- Ref.make(0L)
      yState <- Ref.make(0L)
    } yield PiState(xState, yState)

  def updateState(insideAmountInOneStep: Int, pointsForEachStep: Int, state: PiState): ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.inside.update(_ + insideAmountInOneStep)
      _ <- state.total.update(_ + pointsForEachStep)
    } yield ()

  def printTotal(state: PiState): ZIO[Console, Nothing, Unit] =
    for {
      insideAmount <- state.inside.get
      totalAmount <- state.total.get
      _ <- putStrLn(s"Pi = " + estimatePi(insideAmount, totalAmount))
    } yield ()

  def printCurrentResult(pointsForEachStep: Int, state: PiState): ZIO[Console with Random, Nothing, Unit] =
    for {
      oneStepResult <- calculateForOneTimeStep(pointsForEachStep)
      _ <- updateState(oneStepResult, pointsForEachStep, state)
      _ <- printTotal(state)
    } yield ()

  /**
   * EXERCISE
   *
   * Build a multi-fiber program that estimates the value of `pi`. Print out
   * ongoing estimates continuously until the estimation is complete.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val pointsForEachStep = 100
    (for {
      state <- createState()
      _ <- printCurrentResult(pointsForEachStep, state).repeat(Schedule.recurs(100))
    } yield ()).exitCode
  }
}

object ParallelZip extends App {

  import zio.console._

  def fib(n: Int): UIO[Int] =
    if (n <= 1) UIO(n)
    else
      UIO.effectSuspendTotal {
        (fib(n - 1) zipWith fib(n - 2)) (_ + _)
      }

  /**
   * EXERCISE
   *
   * Compute fib(10) and fib(13) in parallel using `ZIO#zipPar`, and display
   * the result.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (fib(10) zipPar fib(13)).flatMap(t => putStrLn(t.toString)).exitCode
}

object StmSwap extends App {

  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Demonstrate the following code does not reliably swap two values in the
   * presence of concurrency.
   */
  def exampleRef: ZIO[Any with Clock, Nothing, Int] = {
    def swap[A](ref1: Ref[A], ref2: Ref[A]): UIO[Unit] = //even using Refs, that are atomic, the order of the interleaving operations creates problems
      for {
        v1 <- ref1.get
        v2 <- ref2.get
        _ <- ref2.set(v1)
        _ <- ref1.set(v2)
      } yield ()

    for {
      ref1 <- Ref.make(100)
      ref2 <- Ref.make(0)
      fiber1 <- swap(ref1, ref2).repeat(Schedule.recurs(100)).fork //here and below two fibers are swapping the Refs concurrently
      fiber2 <- swap(ref2, ref1).repeat(Schedule.recurs(100)).fork
      _ <- (fiber1 zip fiber2).join
      value <- (ref1.get zipWith ref2.get) (_ + _)
    } yield value
  }

  /**
   * EXERCISE
   *
   * Using `STM`, implement a safe version of the swap function.
   */
  def exampleStm: ZIO[Any with Clock, Nothing, Int] = { //used TRef instead of Ref and STM.commit to mark the atomic code
    def swap[A](ref1: TRef[A], ref2: TRef[A]): UIO[Unit] = //even using Refs, that are atomic, the order of the interleaving operations creates problems
      (for {
        v1 <- ref1.get
        v2 <- ref2.get
        _ <- ref2.set(v1)
        _ <- ref1.set(v2)
      } yield ()).commit

    for {
      ref1 <- TRef.make(100).commit //could use makeCommit instead
      ref2 <- TRef.make(0).commit
      fiber1 <- swap(ref1, ref2).repeat(Schedule.recurs(100)).fork //here and below two fibers are swapping the Refs concurrently
      fiber2 <- swap(ref2, ref1).repeat(Schedule.recurs(100)).fork
      _ <- (fiber1 zip fiber2).join
      value <- (ref1.get.commit zipWith ref2.get.commit) (_ + _)
    } yield value
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    exampleStm.map(_.toString).flatMap(putStrLn(_)).exitCode
}

object StmLock extends App {

  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Using STM, implement a simple binary lock by implementing the creation,
   * acquisition, and release methods.
   */
  class Lock private(tref: TRef[Boolean]) { // this could be also done using ZIO's semaphore
    def acquire: UIO[Unit] =
      (for {
        value <- tref.get
        _ <- STM.check(!value)
        _ <- tref.set(true)
      } yield ()).commit

    def release: UIO[Unit] = tref.set(false).commit
  }

  object Lock {
    def make: UIO[Lock] = TRef.makeCommit(false).map(new Lock(_))
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      lock <- Lock.make
      fiber1 <- lock.acquire
        .bracket_(lock.release)(putStrLn("Bob  : I have the lock!").repeat(Schedule.recurs(10)))
        .fork
      fiber2 <- lock.acquire
        .bracket_(lock.release)(putStrLn("Sarah: I have the lock!").repeat(Schedule.recurs(10)))
        .fork
      _ <- (fiber1 zip fiber2).join
    } yield ExitCode.success
}

object StmQueue extends App {

  import zio.console._
  import zio.stm._
  import scala.collection.immutable.{Queue => ScalaQueue}

  /**
   * EXERCISE
   *
   * Using STM, implement a async queue with double back-pressuring.
   */
  class Queue[A] private(capacity: Int, queue: TRef[ScalaQueue[A]]) {
    def take: UIO[A] =
      (for {
        queueValue <- queue.get
        _ <- STM.check(queueValue.nonEmpty)
        result <- queue.modify(_.dequeue)
      } yield result).commit

    /* alternative implementation using STM.retry instead of STM.check:
    (for {
      queueValue <- queue.get
      result <- queueValue.dequeueOption match {
        case Some((head, tail)) => queue.set(tail) *> STM.succeed(head)
        case None => STM.retry //it will "block" and retry the whole transaction when the queue gets edited
      }
    } yield result).commit */

    def offer(a: A): UIO[Unit] =
      (for {
        queueValue <- queue.get
        _ <- STM.check(queueValue.length < capacity)
        _ <- queue.set(queueValue.enqueue(a))
      } yield ()).commit
  }

  object Queue {
    def bounded[A](capacity: Int): UIO[Queue[A]] =
      TRef.makeCommit(ScalaQueue.empty[A]).map(queue => new Queue(capacity, queue))
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      queue <- Queue.bounded[Int](10)
      _ <- ZIO.foreach(0 to 100)(i => queue.offer(i)).fork
      _ <- ZIO.foreach(0 to 100)(_ => queue.take.flatMap(i => putStrLn(s"Got: ${i}")))
    } yield ExitCode.success
}

object StmLunchTime extends App {

  import zio.console._
  import zio.stm._

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Attendee.
   */
  final case class Attendee(state: TRef[Attendee.State], index: Int) {

    import Attendee.State._

    def isStarving: STM[Nothing, Boolean] = state.get.map(_ == Attendee.State.Starving)

    def feed: STM[Nothing, Unit] = state.set(Attendee.State.Full)
  }

  object Attendee {

    sealed trait State

    object State {

      case object Starving extends State

      case object Full extends State

    }

  }

  /**
   * EXERCISE
   *
   * Using STM, implement the missing methods of Table.
   */
  final case class Table(seats: TArray[Boolean]) {
    def findEmptySeat: STM[Nothing, Option[Int]] =
      seats.fold[(Int, Option[Int])]((0, None)) {
        case ((index, z@Some(_)), _) => (index + 1, z)
        case ((index, None), taken) =>
          (index + 1, if (taken) None else Some(index))
      }.map(_._2)

    def takeSeat(index: Int): STM[Nothing, Unit] = seats.update(index, _ => true)

    def vacateSeat(index: Int): STM[Nothing, Unit] = seats.update(index, _ => false)
  }

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds a single attendee.
   */
  def feedAttendee(t: Table, a: Attendee): STM[Nothing, Unit] = // do NOT use STM.fromFunction(_ => println("someText"))
    for {
      emptySeat <- t.findEmptySeat
      _ <- STM.check(emptySeat.nonEmpty)
      _ <- t.takeSeat(emptySeat.get)
      _ <- a.feed
      _ <- t.vacateSeat(emptySeat.get)
    } yield ()

  /**
   * EXERCISE
   *
   * Using STM, implement a method that feeds only the starving attendees.
   */
  def feedStarving(table: Table, list: List[Attendee]): ZIO[Console, Nothing, Unit] =
    ZIO.foreachPar(list)(attendee => putStrLn(s"Attendee ${attendee.index} wants to eat") *> (for {
      isStarving <- attendee.isStarving
      _ <- if (isStarving) feedAttendee(table, attendee) else STM.unit
    } yield attendee.index).commit
      .flatMap(attendeeIndex => putStrLn(s"Attendee ${attendeeIndex} finished eating"))).unit

  //shows how a limited resource (table) can be used concurrently by multiple users (attendees)
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val attendeeCount = 100
    val tableSize = 5

    for {
      attendees <- ZIO.foreach((0 until attendeeCount).toList)(index => //use toList to avoid canBuildFrom problems
        TRef.make[Attendee.State](Attendee.State.Starving).map(Attendee(_, index)).commit)
      table <- TArray.fromIterable(List.fill(tableSize)(false))
        .map(Table)
        .commit
      _ <- feedStarving(table, attendees.toList)
    } yield ExitCode.success
  }
}

object StmPriorityQueue extends App {

  import zio.console._
  import zio.stm._
  import zio.duration._

  /**
   * EXERCISE
   *
   * Using STM, design a priority queue, where smaller integers are assumed
   * to have higher priority than greater integers.
   */
  // TreeSet can't be used, since it doesn't allow duplicates
  class PriorityQueue[A] private(minLevel: TRef[Option[Int]], map: TMap[Int, TQueue[A]]) {

    def offer(a: A, priority: Int): STM[Nothing, Unit] =
      for {
        _ <- putElementInMap(a, priority)
        _ <- updateMinLevel(a, priority)
      } yield ()

    private def putElementInMap(element: A, priority: Int): STM[Nothing, Unit] =
      for {
        priorityExists <- map.contains(priority)
        _ <- if (!priorityExists) TQueue.unbounded[A].flatMap(map.put(priority, _)) else STM.unit
        queue <- getQueue(priority)
        _ <- queue.offer(element)
      } yield ()

    private def getQueue(priority: Int) = map.get(priority).get.mapError(_ => new Exception()).orDie

    private def updateMinLevel(a: A, priority: Int) =
      for {
        minimum <- minLevel.get
        _ <- if (minimum.isEmpty || minimum.get > priority) minLevel.set(Option(priority)) else STM.unit
      } yield ()

    def take: STM[Nothing, A] =
      for {
        result <- takeMinimumElement()
        _ <- recalculateMinLevel()
      } yield result

    private def takeMinimumElement() =
      for {
        minimumLevel <- minLevel.get
        _ <- STM.check(minimumLevel.nonEmpty)
        queue <- getQueue(minimumLevel.get)
        isQueueEmpty <- queue.isEmpty
        _ <- STM.check(!isQueueEmpty)
        result <- queue.take
      } yield result

    private def recalculateMinLevel() =
      for {
        pairs <- map.toList
        queues <- STM.foreach(pairs.map(_._2))(_.takeAll)
        queuesWithIndexes = pairs.map(_._1) zip queues
        newMinLevel = queuesWithIndexes.filter(_._2.nonEmpty).minOption[(Int, List[A])](Ordering.by(_._1)).map(_._1)
        _ <- minLevel.set(newMinLevel)
      } yield ()
  }

  object PriorityQueue {
    def make[A]: STM[Nothing, PriorityQueue[A]] =
      for {
        minLevel <- TRef.make[Option[Int]](None)
        map <- TMap.make[Int, TQueue[A]]()
      } yield new PriorityQueue[A](minLevel, map)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _ <- putStrLn("Enter any key to exit...")
      queue <- PriorityQueue.make[String].commit
      lowPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(1.millis) *> queue.offer(s"Offer: ${i} with priority 3", 3).commit
      }
      highPriority = ZIO.foreach(0 to 100) { i =>
        ZIO.sleep(2.millis) *> queue.offer(s"Offer: ${i} with priority 0", 0).commit
      }
      _ <- ZIO.forkAll(List(lowPriority, highPriority)) *> queue.take.commit
        .flatMap(putStrLn(_))
        .forever
        .fork *> getStrLn
    } yield 0).exitCode
}

object StmReentrantLock extends App {

  import zio.console._
  import zio.stm._
  import zio.duration._

  private final case class WriteLock(writeCount: Int, readCount: Int, fiberId: Fiber.Id)

  private final class ReadLock private(readers: Map[Fiber.Id, Int]) {
    def total: Int = readers.values.sum

    /*
    def noOtherHolder(fiberId: Fiber.Id): Boolean =
      readers.isEmpty || (readers.size == 1 && readers.contains(fiberId))
     */

    def readLocks(fiberId: Fiber.Id): Int = readers.get(fiberId).fold(0)(identity)

    def adjust(fiberId: Fiber.Id, adjust: Int): ReadLock = {
      val total = readLocks(fiberId)
      val newTotal = total + adjust
      val newReaders = if (newTotal == 0) readers - fiberId else readers.updated(fiberId, newTotal)
      new ReadLock(newReaders)
    }
  }

  private object ReadLock {
    val empty: ReadLock = new ReadLock(Map())

    def apply(fiberId: Fiber.Id, count: Int): ReadLock =
      if (count <= 0) empty else new ReadLock(Map(fiberId -> count))
  }

  /**
   * EXERCISE
   *
   * Using STM, implement a reentrant read/write lock.
   */
  // this works but doesn't guarantee fairness (fiber starving can happen, in particular the ones trying to write)
  class ReentrantReadWriteLock(data: TRef[Either[ReadLock, WriteLock]]) {

    def writeLocked: USTM[Boolean] =
      for {
        fiberId <- STM.fiberId
        data <- data.get
        result = data match {
          case Left(_) => false;
          case Right(value) => value.fiberId != fiberId && (value.writeCount > 0 || value.readCount > 0)
        }
      } yield result

    def writeLocks: USTM[Int] = getReadOrWriteLocksCount(false)

    private def getReadOrWriteLocksCount(getReadLocks: Boolean) =
      for {
        currentData <- data.get
        writeLockCount = currentData match {
          case Left(value) => if(getReadLocks) value.total else 0
          case Right(value) => if(getReadLocks) value.readCount else value.writeCount
        }
      } yield writeLockCount

    def readLocks: USTM[Int] = getReadOrWriteLocksCount(true)

    def readLocked: USTM[Boolean] = for {
      fiberId <- STM.fiberId
      data <- data.get
      result = data match {
        case Left(value) => value.total > value.readLocks(fiberId)
        case Right(value) => value.fiberId != fiberId && value.readCount > 0
      }
    } yield result

    val readLock: Managed[Nothing, Int] = {
      def getAdjustedReadLocks(locks: Either[ReadLock, WriteLock], valueToAdd: Int, fiberId: Fiber.Id) =
        locks match {
          case Left(value) => Left(value.adjust(fiberId, valueToAdd))
          case Right(WriteLock(0, readCount, fiberId)) => Left(ReadLock(fiberId, readCount + valueToAdd))
          case Right(value) => Right(value.copy(readCount = value.readCount + valueToAdd))
        }

      val acquire = (for {
        writeLocked <- writeLocked
        _ <- STM.check(!writeLocked)
        locks <- data.get
        fiberId <- STM.fiberId
        _ <- data.set(getAdjustedReadLocks(locks, 1, fiberId))
        result = locks match {
          case Left(value) => value.readLocks(fiberId) + 1
          case Right(value) => value.readCount + 1
        }
      } yield result).commit

      val release = (for {
        locks <- data.get
        fiberId <- STM.fiberId
        _ <- data.set(getAdjustedReadLocks(locks, -1, fiberId))
      } yield ()).commit

      Managed.make(acquire)(_ => release)
    }

    val writeLock: Managed[Nothing, Int] = {
      val acquire = (for {
        fiberId <- STM.fiberId
        readLocked <- readLocked
        writeLocked <- writeLocked
        _ <- STM.check((!readLocked) && (!writeLocked))
        locks <- data.get
        newWriteLock = locks match {
          case Left(value) => Right(WriteLock(1, value.readLocks(fiberId), fiberId))
          case Right(value) => Right(WriteLock(value.writeCount + 1, value.readCount, fiberId))
        }
        _ <- data.set(newWriteLock)
      } yield newWriteLock.value.writeCount).commit

      val release = (for {
        locks <- data.get
        fiberId <- STM.fiberId
        newLocks = locks match { case Right(value) => Right(value.copy(value.writeCount-1, value.readCount, fiberId)) }
        _ <- data.set(newLocks)
      } yield ()).commit

      Managed.make(acquire)(_ => release)
    }
  }

  object ReentrantReadWriteLock {
    def make: UIO[ReentrantReadWriteLock] = for {
      locks <- TRef.makeCommit[Either[ReadLock, WriteLock]](Left(ReadLock.empty))
    } yield new ReentrantReadWriteLock(locks)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = // use ReentrantReadWriteLockSpec instead to test it
    (for {
      lock <- ReentrantReadWriteLock.make

      readers = ZIO.foreachPar(List(0)) {index =>
        lock.readLock.use(_ => putStrLn(s"$index reads") *> ZIO.sleep(1.second)).forever}
      writers = ZIO.foreachPar(1 to 2) { index =>
        lock.writeLock.use(_ => putStrLn(s"$index writes") *> ZIO.sleep(1.second)).forever}

      reentrantReaders = ZIO.foreachPar(List(3)) {index =>
        lock.readLock.use(_ => lock.readLock.use(_ => putStrLn(s"$index reads") *> ZIO.sleep(1.second))).forever}
      reentrantWriters = ZIO.foreachPar(4 to 5) { index =>
        lock.writeLock.use(_ => lock.writeLock.use(_ => putStrLn(s"$index writes") *> ZIO.sleep(1.second))).forever}

      _ <- ZIO.forkAll(List(readers, writers, reentrantReaders, reentrantWriters))
      _ <- getStrLn
    } yield 0).exitCode
}

object StmDiningPhilosophers extends App {

  import zio.console._
  import zio.stm._

  sealed trait Fork

  val Fork = new Fork {}

  final case class Placement(left: TRef[Option[Fork]], right: TRef[Option[Fork]])

  final case class Roundtable(seats: Vector[Placement])

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to take not one fork, but
   * both forks when they are both available.
   */
  // usually a naive implementation would deadlock, while here STM solves the problem without needing any tricks
  def takeForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]]): STM[Nothing, (Fork, Fork)] =
    for {
      left <- left.get
      right <- right.get
      _ <- STM.check(left.isDefined && right.isDefined)
    } yield (left.get, right.get)

  /**
   * EXERCISE
   *
   * Using STM, implement the logic of a philosopher to release both forks.
   */
  def putForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]])(tuple: (Fork, Fork)): STM[Nothing, Unit] =
    for {
      _ <- left.set(Option(tuple._1))
      _ <- right.set(Option(tuple._2))
    } yield ()

  def setupTable(size: Int): ZIO[Any, Nothing, Roundtable] = {
    val makeFork = TRef.make[Option[Fork]](Some(Fork))

    (for {
      allForks0 <- STM.foreach(0 to size) { i =>
        makeFork
      }
      allForks = allForks0 ++ List(allForks0(0))
      placements = (allForks zip allForks.drop(1)).map {
        case (l, r) => Placement(l, r)
      }
    } yield Roundtable(placements.toVector)).commit
  }

  def eat(philosopher: Int, roundtable: Roundtable): ZIO[Console, Nothing, Unit] = {
    val placement = roundtable.seats(philosopher)

    val left = placement.left
    val right = placement.right

    for {
      forks <- takeForks(left, right).commit
      _ <- putStrLn(s"Philosopher ${philosopher} eating...")
      _ <- putForks(left, right)(forks).commit
      _ <- putStrLn(s"Philosopher ${philosopher} is done eating")
    } yield ()
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val count = 10

    def eaters(table: Roundtable): Iterable[ZIO[Console, Nothing, Unit]] =
      (0 to count).map { index => eat(index, table)}

    for {
      table <- setupTable(count)
      fiber <- ZIO.forkAll(eaters(table))
      _ <- fiber.join
      _ <- putStrLn("All philosophers have eaten!")
    } yield ExitCode.success
  }
}
