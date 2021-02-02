package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.IO
import com.daddykotex.mrep.git.Repository
import com.monovore.decline.Opts
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path

sealed abstract class RunCommand
final case class RunOnDirectories(repos: NonEmptyList[Repository]) extends RunCommand

object RunCommand {
  val repos: Opts[NonEmptyList[Repository]] =
    Opts
      .options[Path](
        long = "repo",
        help = "Points to a repository on your file system."
      )
      .map(_.map(Repository(_)))
}

object RunCommandHandler {
  def handle(command: RunCommand)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO]): IO[Unit] =
    Blocker[IO]
      .use(blocker => {
        implicit val prox = ProxFS2[IO](blocker)

        command match {
          case RunOnDirectories(repos) =>
            repos.traverse { repo =>
              repo.ops.isClean().flatMap(isClean => IO.delay(println(s"$repo $isClean")))
            }.void
        }
      })
}
