package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.effect._
import com.daddykotex.mrep.file._
import com.daddykotex.mrep.git.Repository
import com.monovore.decline.Opts
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path
import com.daddykotex.proc.RawCommand
import com.daddykotex.proc.Exec
import com.daddykotex.proc.ProxRawCommand

sealed abstract class RunCommand
final case class RunOnDirectories(repos: NonEmptyList[Repository], commands: NonEmptyList[RawCommand])
    extends RunCommand

object RunCommand {
  val repos: Opts[NonEmptyList[Repository]] =
    Opts
      .options[Path](
        long = "repo",
        help = "Points to a repository on your file system."
      )
      .map(_.map(Repository(_)))

  val command: Opts[NonEmptyList[RawCommand]] =
    Opts
      .options[String](
        long = "command",
        help = "Command to run on each repository."
      )
      .validate("command argument should not be empty")(_.forall(_.nonEmpty))
      .map(_.map(RawCommand))
}

object RunCommandHandler {
  def handle(command: RunCommand)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO]): IO[Unit] =
    Blocker[IO]
      .use(blocker => {
        implicit val prox = ProxFS2[IO](blocker)
        implicit val fs: FS[IO] = new FileSystem[IO](blocker)
        implicit val exec: Exec[IO, RawCommand] = new ProxRawCommand(prox)

        command match {
          case RunOnDirectories(repos, commands) =>
            repos.traverse { repo =>
              for {
                exists <- fs.exists(repo.directory)
                _ <-
                  IO.raiseWhen(!exists)(new RuntimeException(s"Directory ${repo.directory} does not exist."))

                isClean <- repo.ops.isClean()
                _ <-
                  if (isClean) {
                    commands.traverse(raw => exec.runVoid(raw))
                  } else { IO.unit }
              } yield ()
            }.void
        }
      })
}
