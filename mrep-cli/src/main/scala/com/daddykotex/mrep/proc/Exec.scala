package com.daddykotex.proc

import cats._
import cats.implicits._
import io.github.vigoo.prox.ProxFS2

final case class RawCommand(value: String) extends AnyVal

trait Exec[F[_], A] {
  def runVoid(input: A): F[Unit]
}
class ProxRawCommand[F[_]: MonadError[*[_], Throwable]](prox: ProxFS2[F]) extends Exec[F, RawCommand] {
  import prox._
  private implicit val runner: ProcessRunner[JVMProcessInfo] = new JVMProcessRunner

  private val nonZeroExit: Throwable = new RuntimeException("The execution returned an non zero exit code.")

  override def runVoid(raw: RawCommand): F[Unit] =
    Process(raw.value)
      .run()
      .ensure(nonZeroExit)(_.exitCode.code === 0)
      .void
}
