package com.daddykotex.mrep.repos.gitlab

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.implicits._
import io.circe._, io.circe.generic.semiauto._
import org.http4s.Method._
import org.http4s._
import org.http4s.circe.CirceEntityDecoder
import org.http4s.circe.CirceEntityEncoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.implicits._

private[gitlab] object GitlabJson {
  final case class Project(id: Int, name: String, path_with_namespace: String, ssh_url_to_repo: String)
  implicit val projectDecoder: Decoder[Project] = deriveDecoder[Project]

  final case class ErrorBody(message: NonEmptyList[String])
  implicit val errorBodyDecoder: Decoder[ErrorBody] = deriveDecoder[ErrorBody]

  final case class Tag(name: String)
  implicit val tagDecoder: Decoder[Tag] = deriveDecoder[Tag]

  final case class NewMergeRequest(title: String, description: String, source_branch: String, target_branch: String)
  implicit val newMergeRequestEncoder: Encoder[NewMergeRequest] = deriveEncoder[NewMergeRequest]
}

object GitLabHttpClient {
  class MergeRequestExists(msg: String) extends RuntimeException(msg)
}

final class GitLabHttpClient[G[_]: Applicative: Sync](baseUri: Uri, auth: Authentication, httpClient: Client[G])
    extends Http4sClientDsl[G]
    with CirceEntityDecoder
    with CirceEntityEncoder {

  private implicit class RunRequest(request: G[Request[G]]) {
    def run[T](handle: Response[G] => G[T]): G[T] = {
      request.flatMap(httpClient.run(_).use(handle))
    }
  }

  private def handleError[T](successHandler: Response[G] => G[T]): Response[G] => G[T] = { response =>
    response match {
      case Status.Successful(success) =>
        successHandler(success)
      case Status.ClientError(errorResponse) => raiseFromBody(errorResponse)
      case Status.ServerError(errorResponse) => raiseFromBody(errorResponse)
      case _                                 => raise("Unexpected type of response.")
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
  private def raise[T](message: String) = {
    val ex = message match {
      case msg if msg.startsWith("Another open merge request already exists for this source branch") =>
        new GitLabHttpClient.MergeRequestExists(msg)
      case _ => new RuntimeException(message)
    }
    ex.raiseError[G, T]
  }
  private def raiseFromBody[T](resp: Response[G]): G[T] = {
    resp
      .as[GitlabJson.ErrorBody]
      .flatMap(err => raise[T](err.message.mkString_("\n")))
  }

  private def getRecursively[A](uri: Uri, limit: Option[Long])(implicit ED: EntityDecoder[G, Seq[A]]) = {
    fs2.Stream
      .unfoldLoopEval[G, Uri, Seq[A]](getRequestUri(uri, None)) { uri =>
        val request = GET(uri, Header.Raw("Private-Token".ci, auth.token))
        request.run {
          handleError { success =>
            val nextUri = getNextUri(uri, success)
            success.as[Seq[A]].map(body => (body, nextUri))
          }
        }
      }
      .flatMap(fs2.Stream.emits)
      .through { s => limit.map(x => s.take(x)).getOrElse(s) }
  }

  private def post[A: EntityEncoder[G, *]](body: A, uri: Uri): G[Unit] = {
    val request = POST(body, uri, Header.Raw("Private-Token".ci, auth.token))
    request.run {
      handleError { _ =>
        ().pure[G]
      }
    }
  }

  private def getProjects(projectsUri: Uri): fs2.Stream[G, GitlabJson.Project] = {
    // https://docs.gitlab.com/ee/api/projects.html#list-user-projects
    val uri = projectsUri +? ("archived", false) +? ("min_access_level", 30)
    getRecursively[GitlabJson.Project](uri, None)
  }

  def getAllRepos(): fs2.Stream[G, GitlabRepo] = {
    getProjects(baseUri / "projects").map { project =>
      GitlabRepo(project.name, project.path_with_namespace, project.ssh_url_to_repo)
    }
  }

  def getGroupRepos(group: String): fs2.Stream[G, GitlabRepo] = {
    getProjects(baseUri / "groups" / group / "projects").map { project =>
      GitlabRepo(project.name, project.path_with_namespace, project.ssh_url_to_repo)
    }
  }

  def getLatestTag(repo: GitlabRepo): fs2.Stream[G, Option[GitLabTag]] = {
    val uri = baseUri / "projects" / repo.fullPath / "repository" / "tags"
    getRecursively[GitlabJson.Tag](uri, Some(1)).map { raw => GitLabTag(raw.name) }.last
  }

  def createMergeRequest(
      repo: GitlabRepo,
      branchName: String,
      messages: NonEmptyList[String],
      failIfExists: Boolean
  ) = {
    val NonEmptyList(subject, bodyLines) = messages
    val description = bodyLines.mkString("\n")
    val body = GitlabJson.NewMergeRequest(subject, description, branchName, "master")
    val errorHandling: PartialFunction[Throwable, Unit] = recoverIf(!failIfExists) {
      case _: GitLabHttpClient.MergeRequestExists => ()
    }
    post(body, baseUri / "projects" / repo.fullPath / "merge_requests")
      .recover(errorHandling)
  }

  private def recoverIf[T](recover: Boolean)(pf: PartialFunction[Throwable, T]) = {
    if (recover) { pf }
    else { PartialFunction.fromFunction[Throwable, T](ex => throw ex) }
  }
}
