package com.daddykotex.proc

import cats._
import cats.implicits._
import io.github.vigoo.prox._
import java.nio.file.Path

final case class Command(value: String, args: List[String])

trait Exec[F[_], A] {
  def runVoid(input: A, workDir: Path): F[Unit]
  def runLines(input: A, workDir: Path): F[Vector[String]]
}

class ProxCommand[F[_]: MonadError[*[_], Throwable]](prox: ProxFS2[F]) extends Exec[F, Command] {
  import prox._
  private implicit val runner: ProcessRunner[JVMProcessInfo] = new JVMProcessRunner

  private val nonZeroExit: Throwable = new RuntimeException("The execution returned an non zero exit code.")

  override def runVoid(raw: Command, workDir: Path): F[Unit] = {
    Process(raw.value, raw.args)
      .copy(workingDirectory = workDir.some)
      .run()
      .ensure(nonZeroExit)(_.exitCode.code === 0)
      .void
  }

  override def runLines(raw: Command, workDir: Path): F[Vector[String]] = {
    Process(raw.value, raw.args)
      .copy(workingDirectory = workDir.some)
      .toVector(
        _.through(fs2.text.utf8Decode)
          .through(fs2.text.lines)
          .filter(_.nonEmpty)
      )
      .run()
      .ensure(nonZeroExit)(_.exitCode.code === 0)
      .map { _.output }
  }
}
