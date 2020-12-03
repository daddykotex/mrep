package com.daddykotex.mrep

import cats.effect._
import com.daddykotex.mrep.build.BuildInfo
import com.monovore.decline._
import com.monovore.decline.effect._

object Main
    extends CommandIOApp(
      name = BuildInfo.name,
      header = "A CLI tool to manage your multi-repos.",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    Opts.unit.map { _ =>
      IO.unit.as(ExitCode.Success)
    // DummyMain.run.as(ExitCode.Success)
    }
}
