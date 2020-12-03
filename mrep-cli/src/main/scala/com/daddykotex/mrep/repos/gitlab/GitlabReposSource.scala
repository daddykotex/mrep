package com.daddykotex.mrep.repos.gitlab

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.daddykotex.mrep.repos.ReposSource
import io.circe._, io.circe.generic.semiauto._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._

final case class GitlabRepo(name: String, fullPath: String)
final case class Authentication(token: String)

final class GitlabReposSource[G[_]](gitlabClient: GitLabHttpClient[G]) extends ReposSource[GitlabRepo] {
  locally(gitlabClient)
  override def getRepos[F[_]]: fs2.Stream[F, GitlabRepo] = ???
}

private[gitlab] object GitlabJson {
  final case class Project(id: Int, name: String, path_with_namespace: String)
  implicit val projectDecoder: Decoder[Project] = deriveDecoder[Project]
}

final class GitLabHttpClient[G[_]: Applicative: Sync](baseUri: Uri, auth: Authentication, httpClient: Client[G])
    extends Http4sClientDsl[G]
    with CirceEntityDecoder {

  private implicit class RunRequest(request: G[Request[G]]) {
    def run[T](handle: Response[G] => G[T]): G[T] = {
      request.flatMap(httpClient.run(_).use(handle))
    }
  }

  private def getRequestUri(uri: Uri, maybePage: Option[String]): Uri = {
    maybePage.map(page => uri +? ("page", page)).getOrElse(uri)
  }

  private def getNextUri(uri: Uri, response: Response[G]): Option[Uri] = {
    response.headers
      .find(_.name === "X-Next-Page".ci)
      .filter(_.value.trim().nonEmpty)
      .map(h => getRequestUri(uri, Some(h.value)))
  }

  private def getRecursively[A: EntityDecoder[G, *]](uri: Uri) = {
    fs2.Stream.unfoldLoopEval[G, Uri, A](getRequestUri(uri, None)) { uri =>
      val request = GET(uri, Header.Raw("Private-Token".ci, auth.token))
      request.run { response =>
        val nextUri = getNextUri(uri, response)
        response.as[A].map(body => (body, nextUri))
      }
    }
  }

  private def getProjects(): fs2.Stream[G, GitlabJson.Project] = {
    getRecursively[Seq[GitlabJson.Project]](baseUri / "projects")
      .flatMap(fs2.Stream.emits)
  }

  def getRepos(): fs2.Stream[G, GitlabRepo] = {
    getProjects().map { project =>
      GitlabRepo(project.name, project.path_with_namespace)
    }
  }
}
