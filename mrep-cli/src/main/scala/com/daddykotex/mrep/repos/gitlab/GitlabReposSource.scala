package com.daddykotex.mrep.repos.gitlab

import cats.Applicative
import cats.effect.Sync
import cats.implicits._
import com.daddykotex.mrep.repos.RepoConfigSource
import io.circe.Decoder, io.circe.generic.semiauto._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._
import com.daddykotex.mrep.config._

object GitlabRepo {}
final case class Authentication(token: String)

final class GitlabReposSource[G[_]](gitlabClient: GitLabHttpClient[G]) extends RepoConfigSource[G] {
  override def getRepos: fs2.Stream[G, RepoConfig] =
    gitlabClient.getRepos().map(RenderRepoConfig[GitlabJson.Project].convert(_))
}

private[gitlab] object GitlabJson {
  final case class Project(id: Int, name: String, path_with_namespace: String)
  implicit val projectDecoder: Decoder[Project] = deriveDecoder[Project]

  final case class ErrorBody(error: String)
  implicit val errorBodyDecoder: Decoder[ErrorBody] = deriveDecoder[ErrorBody]

  implicit val grRRConfig: RenderRepoConfig[Project] = RenderRepoConfig.instance { a =>
    RepoConfig(a.name, a.path_with_namespace)
  }
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
    def raise[T](message: String) = new RuntimeException(message).raiseError[G, T]
    def raiseFromBody[T](resp: Response[G]): G[T] = {
      resp
        .as[GitlabJson.ErrorBody]
        .flatMap(err => raise[T](err.error))
    }
    fs2.Stream.unfoldLoopEval[G, Uri, A](getRequestUri(uri, None)) { uri =>
      val request = GET(uri, Header.Raw("Private-Token".ci, auth.token))
      request.run { response =>
        response match {
          case Status.Successful(success) =>
            val nextUri = getNextUri(uri, success)
            success.as[A].map(body => (body, nextUri))
          case Status.ClientError(errorResponse) => raiseFromBody(errorResponse)
          case Status.ServerError(errorResponse) => raiseFromBody(errorResponse)
          case _                                 => raise("Unexpected type of response.")
        }
      }
    }
  }

  private def getProjects(): fs2.Stream[G, GitlabJson.Project] = {
    // https://docs.gitlab.com/ee/api/projects.html#list-user-projects
    val uri = baseUri / "projects" +? ("archived", false) +? ("min_access_level", 30)
    getRecursively[Seq[GitlabJson.Project]](uri)
      .flatMap(fs2.Stream.emits)
  }

  def getRepos(): fs2.Stream[G, GitlabJson.Project] = {
    getProjects()
  }
}
