package net.degoes.zio

import zio._
import java.text.NumberFormat
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object Cat extends App {
  import zio.console._
  import zio.blocking._
  import java.io.IOException
  import scala.io.Source

  /**
   * EXERCISE
   *
   * Using `effectBlockingIO`, implement a function to read a file on the
   * blocking thread pool, storing the result into a string.
   */
  def readFile(file: String): ZIO[Blocking, IOException, String] =
    effectBlockingIO({
      val source = Source.fromFile(file)
      val string = source.mkString
      source.close()
      string
    })

  /**
   * EXERCISE
   *
   * Implement a version of the command-line utility "cat", which dumps the
   * contents of the specified file to standard output.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    readFile(args.head).flatMap(putStrLn(_)).exitCode
}

object CatBracket extends App {
  import zio.console._
  import zio.blocking._
  import java.io.IOException
  import scala.io.Source

  def open(file: String): ZIO[Blocking, IOException, Source] =
    effectBlockingIO(scala.io.Source.fromFile(file))

  def close(source: Source): ZIO[Blocking, IOException, Unit] =
    effectBlockingIO(source.close())

  /**
   * EXERCISE
   *
   * Using `ZIO#bracket`, implement a safe version of `readFile` that cannot
   * fail to close the file, no matter what happens during reading.
   */
  def readFile(file: String): ZIO[Blocking, IOException, String] =
    open(file).bracket(close(_).orDie) { file => ZIO(file.mkString).refineOrDie{case error: IOException => error}}

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      fileName <- ZIO.fromOption(args.headOption)
                   .tapError(_ => putStrLn("You must specify a file name on the command line")) //tapError is used instead of orElse to keep the error
      contents <- readFile(fileName)
      _        <- putStrLn(contents)
    } yield ()).exitCode
}

object SourceManaged extends App {
  import zio.console._
  import zio.blocking._
  import zio.duration._
  import java.io.IOException

  import scala.io.Source

  final class ZSource private (private val source: Source) {
    def execute[T](f: Source => T): ZIO[Blocking, IOException, T] =
      effectBlocking(f(source)).refineToOrDie[IOException]
  }
  object ZSource {

    /**
     * EXERCISE
     *
     * Use the `ZManaged.make` constructor to make a managed data type that
     * will automatically acquire and release the resource when it is used.
     */
    def make(file: String): ZManaged[Blocking, IOException, ZSource] = {
      // An effect that acquires the resource:
      val open = effectBlocking(new ZSource(Source.fromFile(file)))
        .refineToOrDie[IOException]

      // A function that, when given the resource, returns an effect that
      // releases the resource:
      val close: ZSource => ZIO[Blocking, Nothing, Unit] =
        _.execute(_.close()).orDie

      ZManaged.make(open)(close)
    }
  }

  /**
   * EXERCISE
   *
   * Using `ZManaged.foreachPar` and other functions as necessary, implement a function
   * to read the contents of all files in parallel, but ensuring that if anything
   * goes wrong during parallel reading, all files are safely closed.
   */
  def readFiles(files: List[String]): ZIO[Blocking with Console, IOException, List[String]] =
    ZManaged.foreachPar(files)(ZSource.make).use(resources => ZIO.foreachPar(resources)(_.execute(_.mkString)))

  /**
   * EXERCISE
   *
   * Implement a function that prints out all files specified on the
   * command-line. Only print out contents from these files if they
   * can all be opened simultaneously. Otherwise, don't print out
   * anything except an error message.
   */
  // since ZIO.foreachPar returns ZIO[R, E, List[B]], if one of the elements cannot be computed the whole IO fails.
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    readFiles(args).flatMap(lines => putStrLn(lines.mkString)).tapError(_ => putStrLn("Error")).exitCode
}

object CatIncremental extends App {
  import zio.console._
  import zio.blocking._
  import java.io.{ FileInputStream, IOException, InputStream }
  import scala.io.Source

  final case class FileHandle private (private val is: InputStream) {
    final def close: ZIO[Blocking, IOException, Unit] = effectBlockingIO(is.close())

    final def read: ZIO[Blocking, IOException, Option[Chunk[Byte]]] =
      effectBlockingIO {
        val array = Array.ofDim[Byte](1024)
        val len   = is.read(array)
        if (len < 0) None
        else Some(Chunk.fromArray(array).take(len))
      }
  }

  /**
   * EXERCISE
   *
   * Refactor `FileHandle` so that creating it returns a `ZManaged`, so that
   * it is impossible to forget to close an open handle.
   */
  object FileHandle {
    final def open(file: String): ZManaged[Blocking, IOException, FileHandle] = {
      val open = effectBlockingIO(new FileHandle(new FileInputStream(file))).refineToOrDie[IOException]
      val close: FileHandle => ZIO[Blocking, Nothing, Unit] = _.close.orDie
      ZManaged.make(open)(close)
    }
  }

  /**
   * EXERCISE
   *
   * Implement an incremental version of `cat` that pulls a chunk of bytes at
   * a time, stopping when there are no more chunks left.
   */
  def cat(fh: FileHandle): ZIO[Blocking with Console, IOException, Unit] =
    (for {
      currentChunk <- fh.read.map(_.getOrElse(Chunk.empty).map(_.toChar).mkString)
      _ <- putStr(currentChunk) //do not use putStrLn, since that would add a newline between each chunk
    } yield currentChunk.length).flatMap(chunkLength => if(chunkLength > 0) cat(fh) else ZIO.succeed(()))

  /**
   * EXERCISE
   *
   * Implement an incremental version of the `cat` utility, using `ZIO#bracket`
   * or `ZManaged` to ensure the file is closed in the event of error or
   * interruption.
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    args match {
      case file :: Nil =>
        /**
         * EXERCISE
         *
         * Open the specified file, safely create and use a file handle to
         * incrementally dump the contents of the file to standard output.
         */ {
        FileHandle.open(file).use(cat).exitCode
      }

      case _ => putStrLn("Usage: cat <file>") as ExitCode(2)
    }
}
