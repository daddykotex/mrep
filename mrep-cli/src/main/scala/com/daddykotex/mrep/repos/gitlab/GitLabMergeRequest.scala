package com.daddykotex.mrep.repos.gitlab

final case class GitLabMergeRequest(
    id: Int,
    iid: Int,
    title: String,
    description: String,
    source_branch: String,
    target_branch: String,
    state: String
)
