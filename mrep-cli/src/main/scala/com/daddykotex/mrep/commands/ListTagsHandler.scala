package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.effect._
import com.daddykotex.mrep.repos._
import com.daddykotex.mrep.repos.gitlab._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

final case class FetchLatestTag(
    baseUri: Uri,
    token: Authentication,
    groups: NonEmptyList[GitlabGroup],
    matchers: List[NameMatcher]
)

object ListTagHandler {
  def handle(command: FetchLatestTag)(implicit ce: ConcurrentEffect[IO]): IO[Unit] = {
    val stream = for {
      blocker <- fs2.Stream.resource(Blocker[IO])
      client <- fs2.Stream.resource(BlazeClientBuilder[IO](blocker.blockingContext).resource)
      gh = new GitLabHttpClient[IO](command.baseUri, command.token, client)

      _ <- fs2.Stream
        .emits(command.groups.toList)
        .flatMap(g => gh.getGroupRepos(g.value))
        .filter(repo => command.matchers.forall(_.matches(repo.name)))
        .flatMap(repo => gh.getLatestTag(repo).map((repo, _)))
        .evalTap { case (repo, maybeTag) =>
          maybeTag
            .map { tag => IO.delay(println(s"${repo.name}:${tag.name}")) }
            .getOrElse(IO.delay(println(s"No tag for ${repo.name}.")))
        }
    } yield ()
    stream.compile.drain
  }
}
