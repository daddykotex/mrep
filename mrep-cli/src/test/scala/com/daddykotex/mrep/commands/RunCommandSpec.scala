package com.daddykotex.mrep.commands

import cats.syntax.validated._
import com.daddykotex.mrep.proc.Command

class RunCommandSpec extends munit.FunSuite {
  test("parse a single word") {
    assertEquals(RunCommand.stringToCommand("echo"), Command("echo", List.empty).validNel)
  }

  test("parse a command with no quotes") {
    assertEquals(RunCommand.stringToCommand("echo toto"), Command("echo", List("toto")).validNel)
  }

  test("parse a command with double quotes") {
    assertEquals(
      RunCommand.stringToCommand("echo \"toto is a hard to find\""),
      Command("echo", List("toto is a hard to find")).validNel
    )
  }
}
