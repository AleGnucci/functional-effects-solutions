package net.degoes.zio

import java.io.IOException

import zio._

import scala.collection.immutable.Nil
import scala.annotation.tailrec

object Looping extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement a `repeat` combinator using `flatMap` (or `zipRight`) and recursion.
   */
  def repeat[R, E, A](n: Int)(effect: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    if(n>0) effect *> repeat(n-1)(effect) else ZIO.succeed(Chunk.empty)

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    repeat(100)(putStrLn("All work and no play makes Jack a dull boy")).exitCode
}

object Interview extends App {
  import java.io.IOException
  import zio.console._

  val readLine = ZIO.effect(scala.io.StdIn.readLine()).refineOrDie{case error: IOException => error}

  val questions =
    "Where were you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `getAllAnswers` function in such a fashion that it will ask
   * the user each question and collect them all into a list.
   */
  def getAllAnswers(questions: List[String]): ZIO[Console, IOException, List[String]] =
    questions match {
      case Nil     => ZIO.succeed(List[String]())
      case q :: qs => putStrLn(q) *> readLine
        .flatMap(answer => getAllAnswers(qs).map(otherAnswers => answer :: otherAnswers))
    }

  /**
   * EXERCISE
   *
   * Use the preceding `getAllAnswers` function, together with the predefined
   * `questions`, to ask the user a bunch of questions, and print the answers.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    getAllAnswers(questions).flatMap(_.map(putStrLn(_)).reduce(_ *> _)).exitCode
}

object InterviewGeneric extends App {
  import java.io.IOException
  import zio.console._

  val readLine = ZIO.effect(scala.io.StdIn.readLine()).refineOrDie{case error: IOException => error}

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Implement the `iterateAndCollect` function.
   */
  def iterateAndCollect[R, E, A, B](as: List[A])(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = //this is a reimplementation of traverse (ZIO.foreach)
    as match {
      case Nil     => ZIO.succeed(List.empty)
      case a :: as => f(a).flatMap(result => iterateAndCollect(as)(f).map(otherResults => result :: otherResults))
    }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    iterateAndCollect(questions)(putStrLn(_) *> readLine).flatMap(_.map(putStrLn(_)).reduce(_ *> _)).exitCode
}

object InterviewForeach extends App {
  import zio.console._

  val readLine = ZIO.effect(scala.io.StdIn.readLine()).refineOrDie{case error: IOException => error}

  val questions =
    "Where where you born?" ::
      "What color are your eyes?" ::
      "What is your favorite movie?" ::
      "What is your favorite number?" :: Nil

  /**
   * EXERCISE
   *
   * Using `ZIO.foreach`, iterate over each question in `questions`, print the
   * question to the user (`putStrLn`), read the answer from the user
   * (`getStrLn`), and collect all answers into a collection. Finally, print
   * out the contents of the collection.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    ZIO.foreach(questions)(putStrLn(_) *> readLine).flatMap(_.map(putStrLn(_)).reduce(_ *> _)).exitCode
}

object WhileLoop extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the functional effect version of a while loop.
   */
  def whileLoop[R, E, A](cond: UIO[Boolean])(zio: ZIO[R, E, A]): ZIO[R, E, Chunk[A]] =
    cond.flatMap(if(_) zio.flatMap(result => whileLoop(cond)(zio).map(results => Chunk(result) ++ results)) else ZIO.succeed(Chunk.empty))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    def loop(variable: Ref[Int]) =
      whileLoop(variable.get.map(_ < 100)) {
        for {
          value <- variable.get
          _     <- putStrLn(s"At iteration: ${value}")
          _     <- variable.update(_ + 1)
        } yield ()
      }

    (for {
      variable <- Ref.make(0)
      _        <- loop(variable)
    } yield 0).exitCode
  }
}

object Iterate extends App {
  import zio.console._

  /**
   * EXERCISE
   *
   * Implement the `iterate` function such that it iterates until the condition
   * evaluates to false, returning the "last" value of type `A`.
   */
  def iterate[R, E, A](start: A)(cond: A => Boolean)(f: A => ZIO[R, E, A]): ZIO[R, E, A] =
    f(start).flatMap(result => if(cond(result)) iterate(result)(cond)(f) else ZIO.succeed(result))

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    iterate(0)(_ < 100) { i =>
      putStrLn(s"At iteration: ${i}") as (i + 1)
    }.exitCode
}

object TailRecursive extends App {
  import zio.duration._

  trait Response
  trait Request {
    def returnResponse(response: Response): Task[Unit]
  }

  lazy val acceptRequest: Task[Request] = Task(new Request {
    def returnResponse(response: Response): Task[Unit] =
      Task(println(s"Returning response ${response}"))
  })

  def handleRequest(request: Request): Task[Response] = Task {
    println(s"Handling request ${request}")
    new Response {}
  }

  /**
   * EXERCISE
   *
   * Make this infinite loop (which represents a webserver) effectfully tail
   * recursive.
   *
   * ZIO is heap-safe for both *> and >>, the problem is functions that are
   * not tail recursive, and all infinite non-tail recursive functions will
   * consume ever-increasing amounts of heap. Only tail recursive infinite
   * functions can be evaluated forever without consuming ever-increasing
   * amounts of stack / heap.
   * You have to eliminate for comprehension and code using manual flatMap
   * and make sure that your final expression is either a termination or a
   * direct call to the recursive method.
   *
   * https://stackoverflow.com/questions/62861603/is-it-possible-to-do-tail-recursion-on-a-method-which-returns-zio
   *
   * For-comprehensions in Scala are never tail-recursive, and therefore
   * cannot be used in recursive loops without leaking memory (even for
   * trampolined monads!)... unless you use compiler plugin
   * better-monadic-for, which would remove the final map in some cases
   *
   */
  lazy val webserver: Task[Unit] = //methods returning ZIO can't use @tailrec, but scala.util.control.TailCalls and trampolining can be used.
    (for {
      request  <- acceptRequest
      response <- handleRequest(request)
      _        <- request.returnResponse(response)
    } yield ()).forever //the tail recursive call cannot go in a flatMap or map call, so forever was used instead

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fiber <- webserver.fork //now the webserver runs in background
      _     <- ZIO.sleep(100.millis)
      _     <- fiber.interrupt
    } yield ()).exitCode
}
