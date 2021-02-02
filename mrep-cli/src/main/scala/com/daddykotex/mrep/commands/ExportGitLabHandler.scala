package com.daddykotex.mrep.commands

import cats.effect._
import com.daddykotex.mrep.commands.Commands.Validation
import com.daddykotex.mrep.repos.gitlab
import com.daddykotex.mrep.repos.gitlab.GitLabHttpClient
import com.monovore.decline._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import com.daddykotex.mrep.config.ConfigWriter
import java.nio.file.Path
import com.daddykotex.mrep.repos.gitlab.GitlabReposSource

final case class ExportGitLab(baseUri: Uri, token: gitlab.Authentication, path: Path)
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
  def handle(command: ExportGitLab)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO]): IO[Unit] = {
    val withRes = for {
      blocker <- Blocker[IO]
      client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      gh = new GitLabHttpClient[IO](command.baseUri, command.token, client)
      ghSource = new GitlabReposSource[IO](gh)
      cw = new ConfigWriter[IO](command.path, blocker)
      res = ghSource.getRepos.through(cw.toConfigFile()).compile.drain
    } yield res

    withRes.use(identity)
  }
}
