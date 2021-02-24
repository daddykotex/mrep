package com.daddykotex.proc

import cats._
import cats.implicits._
import io.github.vigoo.prox._
import java.nio.file.Path

final class ExecutionError(msg: String) extends RuntimeException(msg)

final case class Command(value: String, args: List[String])

trait Exec[F[_], A] {
  def runVoid(input: A, workDir: Option[Path]): F[Unit]
  def runLines(input: A, workDir: Option[Path]): F[Vector[String]]
}

class ProxCommand[F[_]: MonadError[*[_], Throwable]](prox: ProxFS2[F]) extends Exec[F, Command] {
  import prox._
  private implicit val runner: ProcessRunner[JVMProcessInfo] = new JVMProcessRunner

  private def errorOnNonZero[T](result: prox.ProcessResult[T, String]): F[T] = {
    if (result.exitCode.code === 0) {
      result.output.pure[F]
    } else {
      new ExecutionError(result.error).raiseError[F, T]
    }
  }

  override def runVoid(raw: Command, workDir: Option[Path]): F[Unit] = {
    Process(raw.value, raw.args)
      .copy(workingDirectory = workDir)
      .errorToFoldMonoid(fs2.text.utf8Decode)
      .run()
      .flatMap(errorOnNonZero)
      .void
  }

  override def runLines(raw: Command, workDir: Option[Path]): F[Vector[String]] = {
    Process(raw.value, raw.args)
      .copy(workingDirectory = workDir)
      .toVector(
        _.through(fs2.text.utf8Decode)
          .through(fs2.text.lines)
          .filter(_.nonEmpty)
      )
      .errorToFoldMonoid(fs2.text.utf8Decode)
      .run()
      .flatMap(errorOnNonZero)
  }
}

object Exec {
  object Error {
    def unapply(t: Throwable): Option[ExecutionError] = t match {
      case err: ExecutionError => Some(err)
      case _                   => None
    }
  }
}
