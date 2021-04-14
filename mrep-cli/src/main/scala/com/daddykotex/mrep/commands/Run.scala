package com.daddykotex.mrep.commands

import cats.data.NonEmptyList
import cats.data.Validated
import cats.effect._
import cats.implicits._
import com.daddykotex.mrep.file._
import com.daddykotex.mrep.git.GitCli
import com.daddykotex.mrep.git.Repository
import com.daddykotex.mrep.proc._
import com.daddykotex.mrep.repos.NameMatcher
import com.daddykotex.mrep.repos.gitlab._
import com.daddykotex.mrep.services.HomeService
import com.monovore.decline.Opts
import io.github.vigoo.prox.ProxFS2
import java.nio.file.Path
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder
import scala.concurrent.duration._

sealed abstract class RunCommand
final case class RunOnDirectories(repos: NonEmptyList[Repository], commands: NonEmptyList[Command], allowDirty: Boolean)
    extends RunCommand
final case class RunOnGroups(
    baseUri: Uri,
    token: Authentication,
    groups: NonEmptyList[GitlabGroup],
    matcher: List[NameMatcher],
    branch: String,
    messages: NonEmptyList[String],
    commands: NonEmptyList[Command],
    readOnly: Boolean
) extends RunCommand

object RunCommand {
  val allowDirty: Opts[Boolean] =
    Opts
      .flag(
        long = "allow-dirty",
        help = "Run the command even if the git repository is not clean."
      )
      .orFalse

  val readOnly: Opts[Boolean] =
    Opts
      .flag(
        long = "read-only",
        help = "Run the command but do not care for any changes. (No commits nor merge request will be created)"
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

  val group: Opts[String] =
    Opts
      .option[String](
        long = "group",
        help = "A Gitlab group."
      )
      .mapValidated(Commands.Validation.nonEmptyString)

  val branch: Opts[String] =
    Opts
      .option[String](
        long = "branch",
        help = "The name of the git branch to use in each repository."
      )
      .mapValidated(Commands.Validation.nonEmptyString)

  val messages: Opts[NonEmptyList[String]] =
    Opts
      .options[String](
        long = "message",
        help = """|One message line passed to git commit.
                  |Similar to git-commit `-m` option. A blank message result in a new line.
                  |The first line is used as the merge request subject and the rest as the body.""".stripMargin
      )

  val matchers: Opts[List[NameMatcher]] =
    Opts
      .options[String](
        long = "matcher",
        help = "A regex to match the project name. Use ! before the regex to exclude it."
      )
      .mapValidated(_.traverse(Commands.Validation.nonEmptyString))
      .map(_.map(NameMatcher.fromString))
      .orEmpty

  val commands: Opts[NonEmptyList[Command]] =
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
  def handle(command: RunCommand)(implicit ce: ConcurrentEffect[IO], cs: ContextShift[IO], timer: Timer[IO]): IO[Unit] =
    Blocker[IO]
      .flatMap(blocker => BlazeClientBuilder[IO](blocker.blockingContext).resource.tupleLeft(blocker))
      .use {
        case (blocker, client) => {
          val prox = ProxFS2[IO](blocker)
          val fs: FileSystem[IO] = FileSystem.forSync[IO](blocker)
          implicit val commandExec: Exec[IO, Command] = new ProxCommand(prox)
          val gitCli: GitCli[IO] = GitCli(commandExec)
          val homeService: HomeService[IO] = HomeService[IO](fs)

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
            case RunOnGroups(baseUri, token, groups, matchers, branch, messages, commands, readOnly) =>
              val gh = new GitLabHttpClient[IO](baseUri, token, client)

              val prepareRepository: fs2.Pipe[IO, (GitlabRepo, Repository), (GitlabRepo, Repository)] = { stream =>
                stream.evalTap { case (_, repo) =>
                  val ops = repo.ops[IO]
                  ops.wipeRepository() *> ops.newBranchFromMaster(branch)
                }
              }
              val runCommands: fs2.Pipe[IO, (GitlabRepo, Repository), (GitlabRepo, Repository)] = { stream =>
                stream.evalTap { case (_, repo) =>
                  commands.traverse { raw =>
                    commandExec
                      .runVoid(raw, workDir = Some(repo.directory))
                      .recover { case Exec.Error(ex) =>
                        scribe.error(s"Error running '${raw.value}' in ${repo.directory}", ex)
                      }
                  }
                }
              }

              val publishChanges: fs2.Pipe[IO, (GitlabRepo, Repository), (GitlabRepo, Repository)] = { stream =>
                stream.evalTap { case (_, repo) =>
                  val ops = repo.ops[IO]
                  ops.updateStage() *> ops.commit(messages) *> ops.forcePush(branch)
                }
              }

              val createMr: fs2.Pipe[IO, (GitlabRepo, Repository), (GitlabRepo, Repository)] = { stream =>
                stream.evalTap { case (gitLabRepo, _) =>
                  gh.createMergeRequest(gitLabRepo, branch, messages)
                }
              }

              val writePipe: fs2.Pipe[IO, (GitlabRepo, Repository), (GitlabRepo, Repository)] = { stream =>
                for {
                  el @ (_, repo) <- stream
                  hasDiff <- fs2.Stream.eval(repo.ops[IO].hasDiffFromMaster(branch))
                  res <-
                    if (hasDiff) {
                      fs2.Stream.emit(el).through(publishChanges).through(createMr)
                    } else {
                      fs2.Stream.emit(el)
                    }
                } yield res
              }

              val stream = for {
                home <- fs2.Stream.eval(homeService.getHome())
                _ <- fs2.Stream.eval(homeService.checkOrMake(home))
                repositoriesDirectory <- fs2.Stream.eval(fs.mkdirs(home.path.resolve("repositories")))

                _ <- fs2.Stream.eval(IO.delay(scribe.info(s"Cloning into '$repositoriesDirectory'.")))

                client <- fs2.Stream.resource(BlazeClientBuilder[IO](blocker.blockingContext).resource)
                gh = new GitLabHttpClient[IO](baseUri, token, client)

                _ <- fs2.Stream
                  .emits(groups.toList)
                  .flatMap(g => gh.getGroupRepos(g.value))
                  .filter(repo => matchers.forall(_.matches(repo.name)))
                  .metered(1.second)
                  .evalTap(repo => IO.delay(scribe.info(s"Working in '${repo.fullPath}'.")))
                  .evalMap { repo =>
                    for {
                      resultPath <- fs.mkdirs(repositoriesDirectory.resolve(repo.fullPath))
                      _ <- gitCli
                        .clone(repo.cloneUrl, resultPath)
                        .void
                        .recover(GitCli.ExpectedErrors.ignoreExistingRepositories)
                    } yield (repo, Repository(resultPath))
                  }
                  .through(prepareRepository)
                  .through(runCommands)
                  .through(s => if (readOnly) s else writePipe(s))
              } yield ()

              stream.compile.drain
          }
        }
      }
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
