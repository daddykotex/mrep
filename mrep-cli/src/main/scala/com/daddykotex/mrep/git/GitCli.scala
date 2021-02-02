package com.daddykotex.mrep.git

import cats.Applicative
import cats.implicits._
import io.github.vigoo.prox._
import java.nio.file.Path
import java.nio.file.Paths

sealed trait UntrackedFiles {
  import UntrackedFiles._
  val arg: String = this match {
    case No     => "no"
    case Normal => "normal"
    case All    => "all"
  }
}
object UntrackedFiles {
  case object No extends UntrackedFiles
  case object Normal extends UntrackedFiles
  case object All extends UntrackedFiles
}

trait GitCli[F[_]] {
  val repo: Repository

  def status(untrackedFiles: UntrackedFiles): F[Vector[Path]]
}

object GitCli {
  def forOne[F[_]: Applicative](repository: Repository, prox: ProxFS2[F]): GitCli[F] = new GitCli[F] {
    import prox._

    private implicit val runner: ProcessRunner[JVMProcessInfo] = new JVMProcessRunner

    val repo: Repository = repository

    def status(untrackedFiles: UntrackedFiles): F[Vector[Path]] = {
      Process("git", List("status", s"--untracked-files=${untrackedFiles.arg}"))
        .toVector(
          _.through(fs2.text.utf8Decode)
            .through(fs2.text.lines)
            .filter(_.nonEmpty)
            .map(line => Paths.get(line))
        )
        .run()(runner)
        .map(_.output)
    }
  }
}
