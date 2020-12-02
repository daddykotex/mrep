package com.daddykotex.mrep

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._

object Main
    extends CommandIOApp(
      name = "mrep-cli",
      header = "A CLI tool to manage your multi-repos.",
      version = "0.0.x"
    ) {

  override def main: Opts[IO[ExitCode]] =
    Opts.unit.as(ExitCode.Success.pure[IO])
}
