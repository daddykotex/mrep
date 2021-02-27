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
        commands.traverse { stringToCommand }
      }

  def stringToCommand(rawCommand: String): Validated[NonEmptyList[String], Command] = {
    CommandParserHelper
      .parse(rawCommand)
      .fold(
        _ => "Unable to parse the command.".invalidNel, //todo do something smart with the error
        { case NonEmptyList(head, tail) => Command(head, tail).validNel[String] }
      )
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

private object CommandParserHelper {
  import cats.parse.{Parser => P}

  private val whitespace: P[Unit] = P.charIn(" \t\r\n").void
  private val whitespaces0 = whitespace.rep0.void

  private val spaceChar = ' '
  private val space = P.char(spaceChar)
  private val quoteChar = '"'
  private val quote = P.char(quoteChar)

  private def unquotedStringChars(c: Char) = c != '\\' && c != spaceChar && c != quoteChar
  private val string = P.charsWhile(unquotedStringChars)
  private def quotedStringChars(c: Char) = c != '\\' && c != quoteChar
  private val quotedString = P.charsWhile(quotedStringChars).surroundedBy(quote)

  private val allString = P.repSep(quotedString | string, sep = space)

  private val commandParser = whitespaces0 *> allString <* (whitespaces0 ~ P.end)

  def parse(input: String): Either[P.Error, NonEmptyList[String]] =
    commandParser.parseAll(input)
}
