package com.daddykotex.mrep.repos

class NameMatcherSpec extends munit.FunSuite {
  test("match exact inverse") {
    assert(
      !NameMatcher.fromString("!smartobjects/elasticsearch-hadoop").matches("smartobjects/elasticsearch-hadoop")
    )
  }

  test("match exact") {
    assert(
      NameMatcher.fromString("smartobjects/elasticsearch-hadoop").matches("smartobjects/elasticsearch-hadoop")
    )
  }

  test("match multiple") {
    val matchers = List(
      NameMatcher.fromString("!smartobjects/investigation"),
      NameMatcher.fromString("!smartobjects/elasticsearch-hadoop")
    )
    assertEquals(
      List("smartobjects/first", "smartobjects/investigation", "smartobjects/elasticsearch-hadoop")
        .filter(NameMatcher.predicator(matchers)),
      List("smartobjects/first")
    )
  }
}
