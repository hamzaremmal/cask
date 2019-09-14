package app
import io.undertow.Undertow

import utest._

object ExampleTests extends TestSuite{
  def withServer[T](example: cask.main.BaseMain)(f: String => T): T = {
    val server = Undertow.builder
      .addHttpListener(8080, "localhost")
      .setHandler(example.defaultHandler)
      .build
    server.start()
    val res =
      try f("http://localhost:8080")
      finally server.stop()
    res
  }

  val tests = Tests{
    test("HttpMethods") - withServer(HttpMethods){ host =>
      requests.post(s"$host/login").text() ==> "do_the_login"
      requests.get(s"$host/login").text() ==> "show_the_login_form"
    }
  }
}
