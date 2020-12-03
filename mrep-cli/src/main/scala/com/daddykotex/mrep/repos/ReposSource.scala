package com.daddykotex.mrep.repos

trait ReposSource[Repo] {
  def getRepos[F[_]]: fs2.Stream[F, Repo]
}
