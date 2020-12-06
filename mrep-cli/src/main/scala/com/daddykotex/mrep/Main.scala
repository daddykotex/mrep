package com.daddykotex.mrep

import cats.effect._

import com.daddykotex.mrep.build.BuildInfo
import com.monovore.decline._
import com.monovore.decline.effect._
import com.daddykotex.mrep.commands.Commands
import com.daddykotex.mrep.commands.ExportGitLabHandler
import scala.util.control.NonFatal

object Main
    extends CommandIOApp(
      name = BuildInfo.name,
      header = "A CLI tool to manage your multi-repos.",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    Commands.exportGitLab.map { cmd =>
      ExportGitLabHandler
        .handle(cmd)
        .handleErrorWith { case NonFatal(ex) =>
          ex.printStackTrace()
          IO.pure(ExitCode.Error)
        }
        .as(ExitCode.Success)
    }
}
