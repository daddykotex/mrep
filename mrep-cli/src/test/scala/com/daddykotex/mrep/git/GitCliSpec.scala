package com.daddykotex.mrep.git

import cats.implicits._
import cats.effect._
import java.nio.file.Paths
import java.util.UUID
import io.github.vigoo.prox._
import com.daddykotex.mrep.proc.ProxCommand
import com.daddykotex.mrep.proc.Command
import cats.data.NonEmptyList
import com.daddykotex.mrep.proc.ExecutionError

abstract class BaseRepoSpec extends munit.CatsEffectSuite {
  private val randomFolder = IO.delay(UUID.randomUUID().toString().replace("-", "").take(5))
  protected def setUpGitRepo(initialGitCommands: Vector[Command])(implicit blocker: Blocker): IO[RepoGitCli[IO]] = {
    val targetPath = Paths.get("./target")
    val prox = ProxFS2[IO](blocker)
    import prox.{JVMProcessRunner, Process}
    implicit val runner = new JVMProcessRunner
    val exec = new ProxCommand(prox)

    for {
      randomF <- randomFolder
      workDir = targetPath.resolve(randomF)
      folder <- fs2.io.file.createDirectory[IO](blocker, workDir)
      _ <- Process("git", "init" :: Nil).copy(workingDirectory = Some(workDir)).run()
      _ <- initialGitCommands.traverse(cmd => exec.runVoid(cmd, workDir = Some(workDir)))
    } yield RepoGitCli.forOne[IO](Repository(folder), exec)
  }

  protected def repoGitCliTest(testName: String, init: Vector[Command] = Vector.empty)(
      assert: RepoGitCli[IO] => IO[Unit]
  ): Unit = {
    test(testName) {
      Blocker[IO].use { blocker =>
        setUpGitRepo(init)(blocker).flatMap(assert)
      }
    }
  }

  protected def repositoryOpsTest(testName: String, init: Vector[Command] = Vector.empty)(
      assert: RepositoryOps[IO] => IO[Unit]
  ): Unit = {
    test(testName) {
      Blocker[IO].use { blocker =>
        setUpGitRepo(init)(blocker).flatMap(cli => assert(new GitRepositoryOps(cli)))
      }
    }
  }
}

class RepositoryOpsSpec extends BaseRepoSpec {
  private def addAFile(name: String): Command = Command("touch", name :: Nil)

  private val someCommits: Vector[Command] = Vector(
    addAFile("some-file"),
    Command("git", "add" :: "some-file" :: Nil),
    Command("git", "commit" :: "-m" :: "some-msg" :: Nil)
  )

  repositoryOpsTest("hasDiffFromMaster", someCommits) { ops =>
    ops.hasDiffFromMaster("HEAD").map { res =>
      assertEquals(res, false)
    }
  }

  val unstagedChanges = someCommits ++ Vector(addAFile("other-file"))
  repositoryOpsTest("hasDiffFromMaster", unstagedChanges) { ops =>
    ops.hasDiffFromMaster("HEAD").map { res =>
      assertEquals(res, true)
    }
  }
}

class RepoGitCliSpec extends BaseRepoSpec {
  private val someCommits: Vector[Command] = Vector(
    Command("touch", "some-file" :: Nil),
    Command("git", "add" :: "some-file" :: Nil),
    Command("git", "commit" :: "-m" :: "some-msg" :: Nil)
  )

  repoGitCliTest("diff", someCommits) { cli =>
    cli.diff("HEAD", "master", quiet = false).map { lines =>
      assertEquals(lines, Vector.empty)
    }
  }

  repoGitCliTest("commit fails when no changes") { cli =>
    cli
      .commit(NonEmptyList("fail", Nil))
      .map(_ => fail("expected fail to commit on no changes"))
      .recover { case ex: ExecutionError => assertEquals(ex.getMessage(), "") }
  }
}
