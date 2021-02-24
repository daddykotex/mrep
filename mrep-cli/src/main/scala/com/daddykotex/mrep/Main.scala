package com.daddykotex.mrep

import cats.effect._
import com.daddykotex.mrep.build.BuildInfo
import com.daddykotex.mrep.commands.CloneGitLabHandler
import com.daddykotex.mrep.commands.Commands
import com.daddykotex.mrep.commands.ExportGitLabHandler
import com.daddykotex.mrep.commands.RunCommandHandler
import com.monovore.decline._
import com.monovore.decline.effect._
import scala.util.control.NonFatal

object Main
    extends CommandIOApp(
      name = BuildInfo.name,
      header = "A CLI tool to manage your multi-repos.",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] = {
    val exportGitlab = Commands.exportGitLab.map {
      ExportGitLabHandler.handle
    }

    val cloneGitlab = Commands.cloneCmd.map {
      CloneGitLabHandler.handle
    }
    val run = Commands.run.map { cmd =>
      RunCommandHandler.handle(cmd)
    }

    (exportGitlab orElse run orElse cloneGitlab)
      .map { result =>
        result
          .as(ExitCode.Success)
          .handleErrorWith { case NonFatal(ex) =>
            ex.printStackTrace()
            IO.pure(ExitCode.Error)
          }
      }
  }
}
