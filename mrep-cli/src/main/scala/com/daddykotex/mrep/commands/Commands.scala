package com.daddykotex.mrep.commands

import cats.data.ValidatedNel
import cats.implicits._
import com.monovore.decline._
import org.http4s.Uri

object Commands {
  val uri: Opts[Uri] =
    Opts
      .option[String](
        long = "uri",
        help = "Full uri to your GitLab APIs endpoints. e.g.: https://gitlab.example.com/api/v4"
      )
      .mapValidated(raw => Uri.fromString(raw).leftMap(_.details).toValidatedNel)

  val exportGitLab: Opts[ExportGitLab] =
    Opts.subcommand(name = "export-gitlab-config", help = "Export your GitLab repositories to a configuration file.")(
      (uri, Authentication.gitlabToken).tupled.map((ExportGitLab.apply _).tupled)
    )

  val cloneCmd: Opts[CloneGitLabGroup] =
    Opts.subcommand(name = "clone", help = "Clone multiple Git repositories at once.")(
      (uri, Authentication.gitlabToken, CloneGitLab.repeatedGroup, Files.targetDirectory).tupled
        .map((CloneGitLabGroup.apply _).tupled)
    )

  val run: Opts[RunCommand] =
    Opts.subcommand(name = "run", help = "Run a command against multiple git repositories.")(
      (RunCommand.repos, RunCommand.command).tupled.map((RunOnDirectories.apply _).tupled)
    )

  object Validation {
    def nonEmptyString(raw: String): ValidatedNel[String, String] = {
      if (raw.trim().isEmpty()) {
        "Must not be blank or empty.".invalidNel
      } else {
        raw.validNel
      }
    }
  }
}
