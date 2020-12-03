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

final case class GitlabRepo(name: String, group: String)
final case class Authentication(token: String)

final class GitlabReposSource[G[_]](gitlabClient: GitLabHttpClient[G]) extends ReposSource[GitlabRepo] {
  locally(gitlabClient)
  override def getRepos[F[_]]: fs2.Stream[F, GitlabRepo] = ???
}

object GitlabJson {
  final case class Group(id: Int, name: String, path: String)
  implicit val groupDecoder: Decoder[Group] = deriveDecoder[Group]
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

  def getGroups(): fs2.Stream[G, String] = {
    getRecursively[Seq[GitlabJson.Group]](baseUri / "groups")
      .flatMap(fs2.Stream.emits)
      .map(_.name)
  }
}
