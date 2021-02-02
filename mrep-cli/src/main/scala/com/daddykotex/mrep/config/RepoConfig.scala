package com.daddykotex.mrep.config

import io.circe._
import io.circe.generic.semiauto._

final case class RepoConfig(name: String, fullPath: String)
object RepoConfig {
  implicit val repoConfig: Encoder[RepoConfig] = deriveEncoder
}

trait RenderRepoConfig[T] {
  def convert(value: T): RepoConfig
}

object RenderRepoConfig {
  def apply[T](implicit ev: RenderRepoConfig[T]): RenderRepoConfig[T] = ev
  def instance[T](f: T => RepoConfig): RenderRepoConfig[T] = new RenderRepoConfig[T] {
    override def convert(value: T): RepoConfig = f(value)
  }
}
