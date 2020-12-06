package com.daddykotex.mrep.commands

import cats.data.ValidatedNel
import cats.implicits._
import org.http4s.Uri
import com.monovore.decline._

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
      (uri, ExportGitLab.gitlabToken).tupled.map((ExportGitLab.apply _).tupled)
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
