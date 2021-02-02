package com.daddykotex.mrep.repos

import com.daddykotex.mrep.config.RepoConfig

trait RepoConfigSource[F[_]] {
  def getRepos: fs2.Stream[F, RepoConfig]
}
