package net.degoes.zio

import net.degoes.zio.SourceManaged.ZSource
import zio._
import zio.blocking.{Blocking, effectBlocking}

import scala.io.Source

object AccessEnvironment extends App {
  import zio.console._

  final case class Config(server: String, port: Int)

  /**
   * EXERCISE
   *
   * Using `ZIO.access`, access a `Config` type from the environment, and
   * extract the `server` field from it.
   */
  val accessServer: ZIO[Config, Nothing, String] = ZIO.access(_.server)

  /**
   * EXERCISE
   *
   * Using `ZIO.access`, access a `Config` type from the environment, and
   * extract the `port` field from it.
   */
  val accessPort: ZIO[Config, Nothing, Int] = ZIO.access(_.port)

  def run(args: List[String]) = {
    val config = Config("localhost", 7878)

    (for {
      server <- accessServer
      port   <- accessPort
      _      <- UIO(println(s"Configuration: ${server}:${port}"))
    } yield ExitCode.success).provide(config)
  }
}

object ProvideEnvironment extends App {
  import zio.console._

  final case class Config(server: String, port: Int)

  final case class DatabaseConnection() {
    def query(query: String): Task[Int] = Task(42)
  }

  val getServer: ZIO[Config, Nothing, String] =
    ZIO.access[Config](_.server)

  val useDatabaseConnection: ZIO[DatabaseConnection, Throwable, Int] =
    ZIO.accessM[DatabaseConnection](_.query("SELECT * FROM USERS"))

  /**
   * EXERCISE
   *
   * Compose both the `getServer` and `useDatabaseConnection` effects together.
   * In order to do this successfully, you will have to use `ZIO#provide` to
   * give them the environment that they need in order to run, then they can
   * be composed because their environment types will be compatible.
   */
  def run(args: List[String]) = {
    val config = Config("localhost", 7878)
    val connection = DatabaseConnection()
    (getServer.provide(config) zip useDatabaseConnection.provide(connection)).exitCode
  }
}

object CakeEnvironment extends App {
  import zio.console._
  import java.io.IOException

  trait Logging {
    val logging: Logging.Service
  }
  object Logging {
    trait Service {
      def log(line: String): UIO[Unit]
    }
    def log(line: String) = ZIO.accessM[Logging](_.logging.log(line))
  }
  trait Files {
    val files: Files.Service
  }
  object Files {
    trait Service {
      def read(file: String): IO[IOException, String]
    }
    def read(file: String) = ZIO.accessM[Files](_.files.read(file))
  }

  val effect =
    for {
      file <- Files.read("build.sbt")
      _    <- Logging.log(file)
    } yield ()

  private def readFile(file: String) =
    effectBlocking({
      val source = Source.fromFile(file)
      val string = source.mkString
      source.close()
      string
    }).refineOrDie{case error: IOException => error}

  /**
   * EXERCISE
   *
   * Run `effect` by using `ZIO#provide` to give it what it needs. You will
   * have to build a value (the environment) of the required type
   * (`Files with Logging`).
   */
  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    effect.provide(new Logging with Files {
      override val files: Files.Service = (file: String) => readFile(file).provide(Runtime.default.environment)
      override val logging: Logging.Service = (string: String) => putStrLn(string).provide(Runtime.default.environment)
    }).exitCode
}

object HasMap extends App {
  import zio.console._

  trait Logging
  object Logging extends Logging

  trait Database
  object Database extends Database

  trait Cache
  object Cache extends Cache

  val hasLogging = Has(Logging: Logging)

  val hasDatabase = Has(Database: Database)

  val hasCache = Has(Cache: Cache)

  val allThree = hasLogging ++ hasDatabase ++ hasCache

  val logging  = allThree.get[Logging]
  val database = allThree.get[Database]
  val cache    = allThree.get[Cache]

  private def effectToRun: ZIO[allThree.type, Nothing, Unit] =
    ZIO.fromFunctionM(_ => putStrLn("the effect is running").provide(Runtime.default.environment))

  def run(args: List[String]) = effectToRun.provide(allThree).exitCode
}

object LayerEnvironment extends App {
  import zio.console._
  import java.io.IOException
  import zio.blocking._

  type MyFx = Logging with Files

  type Files = Has[Files.Service]
  object Files {
    trait Service {
      def read(file: String): IO[IOException, String]
    }

    private def readFile(file: String) =
      effectBlocking({
        val source = Source.fromFile(file)
        val string = source.mkString
        source.close()
        string
      }).refineOrDie{case error: IOException => error}

    /**
     * EXERCISE
     *
     * Using `ZLayer.succeed`, create a layer that implements the `Files`
     * service.
     */
    val live: Layer[Nothing, Files] =
      ZLayer.succeed((file: String) => readFile(file).provide(Runtime.default.environment))

    def read(file: String) = ZIO.accessM[Files](_.get.read(file))
  }

  type Logging = Has[Logging.Service]
  object Logging {
    trait Service {
      def log(line: String): UIO[Unit]
    }

    /**
     * EXERCISE
     *
     * Using `ZLayer.fromFunction`, create a layer that requires `Console`
     * and uses the console to provide a logging service.
     */
    val live: ZLayer[Console, Nothing, Logging] =
      ZLayer.fromFunction((console: Console) => (line: String) => putStrLn(line).provide(console))

    def log(line: String) = ZIO.accessM[Logging](_.get.log(line))
  }

  val effect =
    for {
      file <- Files.read("build.sbt")
      _    <- Logging.log(file)
    } yield ()

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {

    /**
     * EXERCISE
     *
     * Run `effect` by using `ZIO#provideCustomLayer` to give it what it needs.
     * You will have to build a value (the environment) of the required type
     * (`Files with Logging`).
     */
    val env: ZLayer[Console, Nothing, Files with Logging] =
      Files.live ++ Logging.live

    effect
      .provideCustomLayer(env)
      .exitCode
  }
}
