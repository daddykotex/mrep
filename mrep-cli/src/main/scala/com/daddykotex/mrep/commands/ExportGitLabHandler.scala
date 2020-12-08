package com.daddykotex.mrep.commands

import cats.effect._
import com.daddykotex.mrep.commands.Commands.Validation
import com.daddykotex.mrep.repos.gitlab
import com.daddykotex.mrep.repos.gitlab.GitLabHttpClient
import com.monovore.decline._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

final case class ExportGitLab(baseUri: Uri, token: gitlab.Authentication)
object ExportGitLab {
  val gitlabToken: Opts[gitlab.Authentication] =
    Opts
      .option[String](
        long = "token",
        help = "Access token to from the GitLab account with API permissions."
      )
      .mapValidated(Validation.nonEmptyString)
      .map(gitlab.Authentication)
}

object ExportGitLabHandler {
  def handle(command: ExportGitLab)(implicit ce: ConcurrentEffect[IO]): IO[Unit] =
    Blocker[IO]
      .flatMap(blocker => BlazeClientBuilder[IO](blocker.blockingContext).resource)
      .use(client => {
        val gh = new GitLabHttpClient[IO](command.baseUri, command.token, client)
        gh.getRepos().debug().take(1).compile.drain
      })
      .flatMap { result => IO.delay(println(result)) }
}
