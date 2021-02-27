package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.data.Validated
import cats.implicits._
import cats.effect._
import com.daddykotex.mrep.file._
import com.daddykotex.mrep.git.Repository
import com.daddykotex.mrep.proc._
import com.monovore.decline.Opts
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path

sealed abstract class RunCommand
final case class RunOnDirectories(repos: NonEmptyList[Repository], commands: NonEmptyList[Command], allowDirty: Boolean)
    extends RunCommand

object RunCommand {
  val allowDirty: Opts[Boolean] =
    Opts
      .flag(
        long = "allow-dirty",
        help = "Run the command even if the git repository is not clean."
      )
      .orFalse
  val repos: Opts[NonEmptyList[Repository]] =
    Opts
      .options[Path](
        long = "repo",
        help = "Points to a repository on your file system.",
        metavar = "/path/to/folder"
      )
      .map(_.map(Repository(_)))

  val command: Opts[NonEmptyList[Command]] =
    Opts
      .options[String](
        long = "command",
        help = "Command to run on each repository."
      )
      .mapValidated { commands =>
        commands
          .traverse { rawCommand =>
            NonEmptyList.fromList(rawCommand.split(" ").toList) match {
              case Some(NonEmptyList(head, tail)) => Command(head, tail).valid
              case None                           => "command argument should not be empty".invalidNel
            }
          }
      }
}

object RunCommandHandler {
  def handle(command: RunCommand)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO]): IO[Unit] =
    Blocker[IO]
      .use(blocker => {
        implicit val prox = ProxFS2[IO](blocker)
        implicit val fs: FS[IO] = new FileSystem[IO](blocker)
        implicit val commandExec: Exec[IO, Command] = new ProxCommand(prox)

        command match {
          case RunOnDirectories(repos, commands, allowDirty) =>
            repos.traverse { repo =>
              for {
                exists <- fs.exists(repo.directory)
                _ <-
                  IO.raiseWhen(!exists)(new RuntimeException(s"Directory ${repo.directory} does not exist."))

                gitRepoOps = repo.ops[IO]

                isClean <- gitRepoOps.isClean()
                _ <-
                  if (isClean || allowDirty) {
                    IO.delay(scribe.debug(s"Running commands on ${repo.directory}")) *>
                      commands.traverse { raw =>
                        commandExec
                          .runVoid(raw, workDir = Some(repo.directory))
                          .recover { case Exec.Error(ex) =>
                            scribe.error(s"Error running '${raw.value}' in ${repo.directory}", ex)
                          }
                      }
                  } else {
                    IO.delay(scribe.info(s"Ignoring ${repo.directory} because it's not clean."))
                  }
              } yield ()
            }.void
        }
      })
}
