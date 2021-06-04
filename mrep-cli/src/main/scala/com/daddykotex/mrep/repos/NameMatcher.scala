package com.daddykotex.mrep.repos

final case class NameMatcher(regex: String, reverse: Boolean) {
  def matches(value: String): Boolean = {
    val switch: Boolean => Boolean = if (reverse) {
      !(_)
    } else {
      identity
    }
    switch(value.matches(regex))
  }
}
object NameMatcher {
  def predicator(matchers: List[NameMatcher]): String => Boolean = { value =>
    matchers.forall { _.matches(value) }
  }

  def fromString(regex: String): NameMatcher = {
    if (regex.startsWith("!")) {
      NameMatcher(regex.drop(1), reverse = true)
    } else {
      NameMatcher(regex, reverse = false)
    }
  }
}
