package com.daddykotex.mrep

import cats.effect._
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import com.daddykotex.mrep.build.BuildInfo

object Main
    extends CommandIOApp(
      name = BuildInfo.name,
      header = "A CLI tool to manage your multi-repos.",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    Opts.unit.as(ExitCode.Success.pure[IO])
}
