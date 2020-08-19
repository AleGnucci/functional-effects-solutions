package net.degoes.zio

import zio._

/*
 * INTRODUCTION
 *
 * ZIO effects model failure, in a way similar to the Scala data types `Try`
 * and `Either`. Unlike exceptions, ZIO effects are statically-typed, allowing
 * you to determine if and how effects fail by looking at type signatures.
 *
 * ZIO effects have a large number of error-related operators to transform
 * and combine effects. Some of these "catch" errors, while others transform
 * them, and still others combine potentially failing effects with fallback
 * effects.
 *
 * In this section, you will learn about all these operators, as well as the
 * rich underlying model of errors that ZIO uses internally.
 */

object ErrorConstructor extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Using `ZIO.fail`, construct an effect that models failure with any
   * string value, such as "Uh oh!". Explain the type signature of the
   * effect.
   */
  val failed: ZIO[Any, String, Nothing] = ZIO.fail("Uh oh")

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.foldM(putStrLn(_), putStrLn(_)) as ExitCode.success
}

object ErrorRecoveryOrElse extends App {
  import zio.console._

  val failed = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#orElse` have the `run` function compose the preceding `failed`
   * effect with another effect that succeeds with a success exit code.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.orElse(ZIO.succeed(ExitCode.success))
}

object ErrorShortCircuit extends App {
  import zio.console._

  val failed =
    putStrLn("About to fail...") *>
      ZIO.fail("Uh oh!") *>
      putStrLn("This will NEVER be printed!")

  /**
   * EXERCISE
   *
   * Using `ZIO#orElse` have the `run` function compose the
   * preceding `failed` effect with another effect that
   * succeeds with an exit code (created with `ZIO.succeed`).
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.orElse(ZIO.succeed()).exitCode
}

object ErrorRecoveryFold extends App {
  import zio.console._

  val failed = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#fold`, map both failure and success values of `failed` into
   * exit codes.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.fold(_ => ExitCode.failure, _ => ExitCode.success)
}

object ErrorRecoveryCatchAll extends App {
  import zio.console._

  val failed = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#catchAll`, catch all errors in `failed` and print them out to
   * the console using `putStrLn`.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.catchAll(putStrLn(_)).exitCode
}

object ErrorRecoveryFoldM extends App {
  import zio.console._

  val failed: ZIO[Any, String, String] = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#foldM`, print out the success or failure value of `failed`
   * by using `putStrLn`.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.foldM(putStrLn(_), putStrLn(_)).exitCode
}

object ErrorRecoveryEither extends App {
  import zio.console._

  val failed: ZIO[Any, String, Int] = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#either`, surface the error of `failed` into the success
   * channel, and then map the `Either[String, Int]` into an exit code.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.either.map({
      case Left(_) => ExitCode.failure
      case Right(_) => ExitCode.success
    })
}

object ErrorRecoveryIgnore extends App {
  import zio.console._

  val failed: ZIO[Any, String, Int] = ZIO.fail("Uh oh!")

  /**
   * EXERCISE
   *
   * Using `ZIO#ignore`, simply ignore the failure of `failed`, and then map
   * the resulting unit into a successful exit code.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.ignore.map(_ => ExitCode.success)
}

object ErrorNarrowing extends App {
  import java.io.IOException
  import scala.io.StdIn.readLine

  val broadReadLine: IO[Throwable, String] = ZIO.effect(scala.io.StdIn.readLine())

  /**
   * EXERCISE
   *
   * Using `ZIO#refineToOrDie`, narrow the error type of `broadReadLine` into
   * an `IOException`:
   */
  val myReadLine: IO[IOException, String] = broadReadLine.refineOrDie{case error: IOException => error}

  def myPrintLn(line: String): UIO[Unit] = UIO(println(line))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _    <- myPrintLn("What is your name?")
      name <- myReadLine
      _    <- myPrintLn(s"Good to meet you, ${name}")
    } yield ()).exitCode
}

object AlarmApp extends App {
  import zio.console._
  import zio.duration._
  import java.io.IOException
  import java.util.concurrent.TimeUnit
  import scala.io.StdIn
  import zio.internal.Platform

  /**
   * EXERCISE
   *
   * Create an effect that will get a `Duration` from the user, by prompting
   * the user to enter a decimal number of seconds. Use `refineToOrDie` to
   * narrow the error type as necessary.
   */
  lazy val getAlarmDuration: ZIO[Console, IOException, Duration] = {
    def parseDuration(): IO[NumberFormatException, Duration] =
      ZIO.effect(StdIn.readLine())
        .flatMap(string => IO.effect(Duration(string.toLong, TimeUnit.SECONDS))) //using map and removing IO.effect causes ZIO to not handle the NumberFormatException, since that's what ZIO.effect does
        .refineOrDie{case error: NumberFormatException => error}

    def fallback(): ZIO[Console, IOException, Duration] =
      putStrLn("uh oh") *> ZIO.succeed(Duration.Zero)

    for {
      _        <- putStrLn("Please enter the number of seconds to sleep: ")
      duration <- parseDuration() orElse fallback()
    } yield duration
  }

  /**
   * EXERCISE
   *
   * Create a program that asks the user for a number of seconds to sleep,
   * sleeps the specified number of seconds using `ZIO.sleep(d)`, and then
   * prints out a wakeup alarm message, like "Time to wakeup!!!".
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      sleepDuration <- getAlarmDuration
      _ <- ZIO.sleep(sleepDuration)
      _ <- putStrLn("Time to wakeup!!!")
    } yield ExitCode.success).orDie
}

object SequentialCause extends App {
  import zio.console._

  val failed1 = Cause.fail("Uh oh 1")
  val failed2 = Cause.fail("Uh oh 2")

  /**
   * EXERCISE
   *
   * Using `Cause.++`, form a sequential cause by composing `failed1`
   * and `failed2`.
   */
  lazy val composed = failed1 ++ failed2

  /**
   * EXERCISE
   *
   * Using `Cause.prettyPrint`, dump out `composed` to the console.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    putStrLn(composed.prettyPrint).exitCode
}

object ParalellCause extends App {
  import zio.console._

  val failed1 = Cause.fail("Uh oh 1")
  val failed2 = Cause.fail("Uh oh 2")

  /**
   * EXERCISE
   *
   * Using `Cause.&&`, form a parallel cause by composing `failed1`
   * and `failed2`.
   */
  lazy val composed = failed1 && failed2

  /**
   * EXERCISE
   *
   * Using `Cause.prettyPrint`, dump out `composed` to the console.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    putStrLn(composed.prettyPrint).exitCode
}

object Sandbox extends App {
  import zio.console._
  import zio.internal.Platform
  override val platform: Platform = Platform.default.withReportFailure(_ => ()) // this disables the warnings of not handled errors (exceptions catched by sandbox would also cause warnings)

  val failed1    = ZIO.fail("Uh oh 1")
  val failed2    = ZIO.fail("Uh oh 2")
  val finalizer1 = ZIO.fail(new Exception("Finalizing 1!")).orDie
  val finalizer2 = ZIO.fail(new Exception("Finalizing 2!")).orDie

  val composed = ZIO.uninterruptible { //this IO cannot be interrupted from outside, only from inside
    (failed1 ensuring finalizer1) zipPar (failed2 ensuring finalizer2)
  }

  /**
   * EXERCISE
   *
   * Using `ZIO#sandbox`, sandbox the `composed` effect and print out the
   * resulting `Cause` value to the console using `putStrLn`.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    composed.sandbox.cause.flatMap(cause => putStrLn(cause.prettyPrint)).exitCode
}
