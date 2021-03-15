package com.daddykotex.mrep.commands

import cats.effect._
import com.daddykotex.mrep.repos.gitlab._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

final case class ExportGitLab(baseUri: Uri, token: Authentication)
object ExportGitLabHandler {
  def handle(command: ExportGitLab)(implicit ce: ConcurrentEffect[IO]): IO[Unit] =
    Blocker[IO]
      .flatMap(blocker => BlazeClientBuilder[IO](blocker.blockingContext).resource)
      .use { client =>
        val gh = new GitLabHttpClient[IO](command.baseUri, command.token, client)
        gh.getAllRepos().debug().take(1).compile.drain
      }
      .flatMap { result => IO.delay(println(result)) }
}
