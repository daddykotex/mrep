package com.daddykotex.mrep.commands

import com.daddykotex.mrep.commands.Commands.Validation
import com.daddykotex.mrep.repos.gitlab
import com.monovore.decline.Opts
import java.nio.file.Path

object Authentication {
  val gitlabToken: Opts[gitlab.Authentication] =
    Opts
      .option[String](
        long = "token",
        help = "Access token to from the GitLab account with API permissions."
      )
      .mapValidated(Validation.nonEmptyString)
      .map(gitlab.Authentication)

}

object Files {
  val targetDirectory: Opts[Path] = Opts.argument[Path]()
}
