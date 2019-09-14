package cask.endpoints

import java.io.{ByteArrayOutputStream, InputStream, OutputStream, OutputStreamWriter}

import cask.internal.{Router, Util}
import cask.main.Endpoint
import cask.model.{Request, Response}
import collection.JavaConverters._

sealed trait JsReader[T] extends Router.ArgReader[ujson.Value, T, cask.model.Request]
object JsReader{
  implicit def defaultJsReader[T: upickle.default.Reader] = new JsReader[T]{
    def arity = 1

    def read(ctx: cask.model.Request, label: String, input: ujson.Value): T = {
      val reader = implicitly[upickle.default.Reader[T]]
      upickle.default.read[T](input)(reader)
    }
  }

  implicit def paramReader[T: ParamReader] = new JsReader[T] {
    override def arity = 0

    override def read(ctx: cask.model.Request, label: String, v: ujson.Value) = {
      implicitly[ParamReader[T]].read(ctx, label, Nil)
    }
  }
}
trait JsonData extends Response.Data
object JsonData{
  implicit class JsonDataImpl[T: upickle.default.Writer](t: T) extends JsonData{
    def write(out: OutputStream) = {
      val writer = new OutputStreamWriter(out)
      implicitly[upickle.default.Writer[T]].write(new ujson.BaseRenderer(writer), t)
      writer.flush()
    }
  }
}

class postJson(val path: String, override val subpath: Boolean = false) extends Endpoint{
  type InnerReturned = Response[JsonData]
  val methods = Seq("post")
  type Input = ujson.Value
  type InputParser[T] = JsReader[T]
  override type OuterReturned = Router.Result[Response.Raw]
  def wrapFunction(ctx: Request,
                   delegate: Delegate): Router.Result[Response.Raw] = {
    val obj = for{
      str <-
        try {
          val boas = new ByteArrayOutputStream()
          Util.transferTo(ctx.exchange.getInputStream, boas)
          Right(new String(boas.toByteArray))
        }
        catch{case e: Throwable => Left(cask.model.Response(
          "Unable to deserialize input JSON text: " + e + "\n" + Util.stackTraceString(e),
          statusCode = 400
        ))}
      json <-
        try Right(ujson.read(str))
        catch{case e: Throwable => Left(cask.model.Response(
          "Input text is invalid JSON: " + e + "\n" + Util.stackTraceString(e),
          statusCode = 400
        ))}
      obj <-
        try Right(json.obj)
        catch {case e: Throwable => Left(cask.model.Response(
          "Input JSON must be a dictionary",
          statusCode = 400
        ))}
    } yield obj.toMap
    obj match{
      case Left(r) => Router.Result.Success(r.map(Response.Data.StringData))
      case Right(params) => delegate(params).map(_.data)
    }
  }
  def wrapPathSegment(s: String): Input = ujson.Str(s)
}

class getJson(val path: String, override val subpath: Boolean = false) extends Endpoint{
  type InnerReturned = Response[JsonData]
  val methods = Seq("get")
  type Input = Seq[String]
  type InputParser[T] = QueryParamReader[T]
  override type OuterReturned = Router.Result[Response.Raw]
  def wrapFunction(ctx: Request,
                   delegate: Delegate): Router.Result[Response.Raw] = {

    val res = delegate(WebEndpoint.buildMapFromQueryParams(ctx))

    res.map(_.data)
  }
  def wrapPathSegment(s: String) = Seq(s)
}