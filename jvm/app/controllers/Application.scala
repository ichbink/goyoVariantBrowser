package controllers

import java.util.UUID
import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import boopickle.Default._
import com.sksamuel.avro4s.{SchemaFor, ToRecord, AvroSchema}
//import kafkaConnector.consumer.{VariantConsumer}
//import kafkaConnector.consumer.{VariantConsumer}
import play.api.Logger
import play.api.libs.concurrent.Akka
import shared._
import shared.variants._

import play.api.mvc._
import javax.inject._

import scala.concurrent.Future
import scala.reflect.runtime._

//import kafkaConnector.producer.{TestAkkProducer}

import scala.scalajs.js.typedarray.ArrayBuffer
import kafkaConnector.producer.VariantStream
import play.api.inject.ApplicationLifecycle

class ApiImpl @Inject( )(system: ActorSystem) extends GoyoVariantBrowserAPI {

  //Code for variantBrowser

  //val variantProducer = new kafkaConnector.producer.myProducer[Variant]("variants")
  //val variantConsumer = new Consumer[Variant]("variants")



  //val variantProducer = new TestAkkProducer[Variant](system, "variants")
 // val variantConsumer = new VariantConsumer[Variant](system,"variants")

  override def persistVariants( uuid:  UUID, variants : Seq[Variant] ) : Int = {
    Logger.info(s"Persist ${variants.size} variants into server")
/*
    variants.map( //v => variantProducer.send(  v) )
      v => VariantStream.send( v,uuid ))
//    variants.map( v => variantProducer.send(  Pickle.intoBytes(v)  ) )
  */
    VariantStream.send( variants,uuid)
    variants.size
  }

  override def getVariants( uuid: UUID, filters : Seq[VariantFilter], pPass : Tuple2[Int,Int], pFilter : Tuple2[Int, Int] ) : Future[VariantList] = {
    Logger.info(s"Time to stream some variants with filters : ${filters} from $pPass, and  $pFilter")
    import scala.concurrent.ExecutionContext.Implicits.global
    VariantStream.getFilteredVariants( uuid, filters, pPass, pFilter )
  //  Future { VariantList(Seq(), 0 ,0 ) }
  }
}





object Router extends autowire.Server[java.nio.ByteBuffer, Pickler, Pickler] {
  override def read[R: Pickler](p: java.nio.ByteBuffer) = Unpickle[R].fromBytes(p)
  override def write[R: Pickler](r: R) = Pickle.intoBytes(r)
}




class Application @Inject()(webJarAssets :  WebJarAssets , system: ActorSystem, lifecycle: ApplicationLifecycle ) extends Controller {

  def  index = Action {
    Ok(views.html.index("Your new application is ready.", webJarAssets))
  }

  val goyoVariantBrowserApiImpl = new  ApiImpl(system)

  def autowireApi(path: String) = Action.async(parse.raw) {  implicit request =>
    val b = request.body.asBytes(parse.UNLIMITED).get
    val j = Unpickle[Map[String, ByteBuffer]].fromBytes(ByteBuffer.wrap(b.toArray[Byte]))
    Logger.info(s"received autowireApi to ${path} with and ${j}")
   // Logger.info(s"received autowireApi to ${path} with ${request.session.get("username")} and ${j}")
   // implicit val usr = request.session.get("username : ")
    import scala.concurrent.ExecutionContext.Implicits.global
    Logger.info(s"Unpickle from request: $j")
    Router.route[GoyoVariantBrowserAPI](goyoVariantBrowserApiImpl)(
      autowire.Core.Request( path.split("/"), j )
    ).map { buffer => {
      Logger.info(s"Map implementation response")
      val data = Array.ofDim[Byte](buffer.remaining())
      buffer.get(data)
      Logger.info(s"got something from method $data")
      Ok(data)
    }
    }
  }

  lifecycle.addStopHook { () =>
    VariantStream.stop
  }


}