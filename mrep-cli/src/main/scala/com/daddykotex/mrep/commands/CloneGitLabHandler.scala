package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.daddykotex.mrep.commands.Commands.Validation
import com.daddykotex.mrep.file._
import com.daddykotex.mrep.git.GitCli
import com.daddykotex.mrep.repos.gitlab
import com.daddykotex.mrep.repos.gitlab.GitLabHttpClient
import com.daddykotex.proc._
import com.monovore.decline.Opts
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

final case class GitlabGroup(value: String)
final case class CloneGitLabGroup(
    baseUri: Uri,
    token: gitlab.Authentication,
    groups: NonEmptyList[GitlabGroup],
    targetDirectory: Path
)

object CloneGitLab {
  val group: Opts[GitlabGroup] =
    Opts
      .option[String](
        long = "group",
        help = "Group that exists in GitLab."
      )
      .mapValidated(Validation.nonEmptyString)
      .map(GitlabGroup)

  val repeatedGroup: Opts[NonEmptyList[GitlabGroup]] =
    Opts
      .options[String](
        long = "group",
        help = "Group that exists in GitLab."
      )
      .mapValidated(_.traverse(Validation.nonEmptyString))
      .map(_.map(GitlabGroup))
}

object CloneGitLabHandler {
  def handle(command: CloneGitLabGroup)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO]): IO[Unit] = {
    val stream = for {
      blocker <- fs2.Stream.resource(Blocker[IO])
      fs: FS[IO] = new FileSystem[IO](blocker)

      exists <- fs2.Stream.eval(fs.exists(command.targetDirectory))
      _ <- fs2.Stream.eval(
        IO.raiseWhen(!exists)(new RuntimeException(s"Directory ${command.targetDirectory} does not exist."))
      )

      client <- fs2.Stream.resource(BlazeClientBuilder[IO](blocker.blockingContext).resource)

      prox = ProxFS2[IO](blocker)
      commandExec: Exec[IO, Command] = new ProxCommand(prox)
      gitCli: GitCli[IO] = GitCli(commandExec)
      gh = new GitLabHttpClient[IO](command.baseUri, command.token, client)

      _ <- fs2.Stream
        .emits(command.groups.toList)
        .flatMap(g => gh.getGroupRepos(g.value))
        .evalMap { repo =>
          for {
            resultPath <- fs.mkdirs(command.targetDirectory.resolve(repo.fullPath))
            _ <- gitCli
              .clone(repo.cloneUrl, resultPath)
              .void
              .recover(GitCli.ExpectedErrors.ignoreExistingRepositories)
          } yield ()
        }
    } yield ()
    stream.compile.drain
  }
}
