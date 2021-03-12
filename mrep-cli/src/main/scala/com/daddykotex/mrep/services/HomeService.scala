package com.daddykotex.mrep.services

import com.daddykotex.mrep.MRepException
import com.daddykotex.mrep.file.FileSystem
import java.nio.file.Path
import java.nio.file.Paths
import cats.implicits._
import scala.util.Try
import cats.MonadError

final case class Home(path: Path, shouldExist: Boolean)

trait HomeService[F[_]] {
  def getHome(): F[Home]
  def checkOrMake(home: Home): F[Unit]
}

object HomeService {
  def apply[F[_]](fs: FileSystem[F])(implicit ME: MonadError[F, Throwable]): HomeService[F] = {
    def optionalSysProp(key: String) = Option(System.getProperty(key))
    new MRepHomeService[F](sys.env, optionalSysProp, fs)
  }
}

class MRepHomeService[F[_]](
    env: Map[String, String],
    sysPropsF: String => Option[String],
    fs: FileSystem[F]
)(implicit ME: MonadError[F, Throwable])
    extends HomeService[F] {
  private val overridenMrepHome = env
    .get("MREP_HOME")
    .map(path => Paths.get(path))
    .map(path => Home(path, shouldExist = true))

  private val defaultMrep =
    Try(sysPropsF("user.home")).toOption.flatten
      .map(home => s"$home/.mrep")
      .map(path => Paths.get(path))
      .map(path => Home(path, shouldExist = false))

  def getHome(): F[Home] = {
    overridenMrepHome
      .orElse(defaultMrep)
      .liftTo[F](new MRepException(s"Unable to compute default home directory."))
  }

  def checkOrMake(home: Home): F[Unit] = {
    if (home.shouldExist) {
      fs.exists(home.path)
        .flatMap(check =>
          ME.whenA(check)(ME.raiseError(new MRepException(s"Overriden home at '${home.path}' does not exists.")))
        )
    } else {
      fs.mkdirs(home.path).as(())
    }
  }
}
