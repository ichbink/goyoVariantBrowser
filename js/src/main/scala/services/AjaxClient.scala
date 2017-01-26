package services

/**
  * Created by jcsilla on 1/07/16.
  */

import java.nio.ByteBuffer

import boopickle.Default._
import org.scalajs.dom

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}


object AjaxClient extends autowire.Client[ByteBuffer,Pickler, Pickler] {

  override def read[Result: Pickler](p: ByteBuffer) = Unpickle[Result].fromBytes(p)
  override def write[Result: Pickler](r: Result) = Pickle.intoBytes(r)

  override def doCall(req: Request) : Future[ByteBuffer] = {
    dom.console.log (s"doCall: ${req}")
    dom.ext.Ajax.post(
      url = "/api/" + req.path.mkString("/"),
      data = Pickle.intoBytes(req.args),
      responseType = "arraybuffer",
      headers = Map("Content-Type" -> "application/octet-stream")
    ).map{ r =>
     // dom.console.log(f"Success from $req}")
      TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer])
    }
  }
}