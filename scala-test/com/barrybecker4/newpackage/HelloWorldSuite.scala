package com.barrybecker4.newpackage

import org.scalatest.FunSuite

class HelloWorldSuite extends FunSuite {

  test("some test") {
    assertResult("Hello world!") {
      HelloWorld.h()
    }
  }
}