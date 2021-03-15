package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import com.daddykotex.mrep.commands.Commands.Validation
import com.daddykotex.mrep.repos.gitlab.{Authentication => GitLabAuth, _}
import com.monovore.decline.Opts
import java.nio.file.Path

object Authentication {
  val gitlabToken: Opts[GitLabAuth] =
    Opts
      .option[String](
        long = "token",
        help = "Access token to from the GitLab account with API permissions."
      )
      .mapValidated(Validation.nonEmptyString)
      .map(GitLabAuth.apply)

}

object Files {
  val targetDirectory: Opts[Path] = Opts.argument[Path]()
}

object GitLab {
  val repeatedGroup: Opts[NonEmptyList[GitlabGroup]] =
    Opts
      .options[String](
        long = "group",
        help = "Group that exists in GitLab."
      )
      .mapValidated(_.traverse(Validation.nonEmptyString))
      .map(_.map(GitlabGroup))
}
