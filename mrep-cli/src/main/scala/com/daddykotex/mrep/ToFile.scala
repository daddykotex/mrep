import fs2._
import fs2.concurrent._
import cats.effect._

import java.nio.file.Paths

object WriteToFile {

  def main(args: Array[String]): Unit = {
    // Defining implicits
    implicit val ioContextShift: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

    val blocker: Blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.Implicits.global)

    val program2 = for {
      queue <- Queue.unbounded[IO, Option[String]]
      writeToFileInstance <- newWriteToFile2(queue, blocker)
      _ <- writeToFileInstance.write("string1")
      _ <- writeToFileInstance.write("string2")
      _ <- writeToFileInstance.close()
    } yield ()

    program2.unsafeRunSync()
  }

  def toFile(filename: String, blocker: Blocker)(implicit cs: ContextShift[IO]): Pipe[IO, String, Unit] =
    _.through(text.utf8Encode)
      .through(io.file.writeAll(Paths.get(filename), blocker = blocker))

  def newWriteToFile2(queue: Queue[IO, Option[String]], blocker: Blocker)(implicit
      cs: ContextShift[IO]
  ): IO[WriteToFile2] = {
    val writeToDisk: IO[Unit] =
      queue.dequeue.unNoneTerminate.through(toFile("/Users/david/Desktop/function.txt", blocker)).compile.drain

    writeToDisk.start.map { fiber =>
      new WriteToFile2() {
        def write(value: String): IO[Unit] = {
          queue.enqueue1(Some(value))
        }
        def close(): IO[Unit] = queue.enqueue1(None) *> fiber.join
      }
    }
  }
}

trait WriteToFile2 {
  def write(value: String): IO[Unit]
  def close(): IO[Unit]
}
