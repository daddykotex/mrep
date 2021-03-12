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
      (uri, Authentication.gitlabToken, GitLab.repeatedGroup, Files.targetDirectory).tupled
        .map((CloneGitLabGroup.apply _).tupled)
    )

  val runGroup: Opts[RunCommand] =
    Opts.subcommand(name = "run-group", help = "Run a command against multiple git repositories in a Group.") {
      (
        uri,
        Authentication.gitlabToken,
        GitLab.repeatedGroup,
        RunCommand.matchers,
        RunCommand.branch,
        RunCommand.messages,
        RunCommand.commands
      ).tupled
        .map((RunOnGroups.apply _).tupled)
    }

  val runDir: Opts[RunCommand] =
    Opts.subcommand(name = "run-dir", help = "Run a command against multiple git repositories.") {
      (RunCommand.repos, RunCommand.commands, RunCommand.allowDirty).tupled.map((RunOnDirectories.apply _).tupled)
    }

  val run = runDir orElse runGroup

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
