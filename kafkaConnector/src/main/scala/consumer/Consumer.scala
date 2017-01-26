package kafkaConnector.consumer

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/*
import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer
import akka.kafka.{Subscriptions, ConsumerSettings}
import akka.kafka.scaladsl.Consumer.plainSource
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import boopickle.{Encoder, Pickler, UnpickleImpl}
import com.sksamuel.avro4s.{FromRecord, AvroSchema, SchemaFor, AvroInputStream}
import org.apache.kafka.clients.consumer.internals.PartitionAssignor.Subscription
import org.apache.kafka.clients.consumer.{KafkaConsumer, ConsumerConfig}
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}
import java.util.{UUID, Properties}
import org.apache.kafka.streams.kstream.KStreamBuilder
import shared.variants.{VariantFilter, VariantList, Variant}
import org.apache.kafka.common.serialization.{StringDeserializer,ByteArrayDeserializer}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

*/
/*

class VariantConsumer[A](system : ActorSystem, topic : String ){

  implicit val actorSystem = system
  implicit val materializer = ActorMaterializer()

  implicit val  schema = SchemaFor[Variant]
  implicit val  fr = FromRecord[Variant]

  val consumerSettings = ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers("localhost:9092")
      .withGroupId("group1")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")


  val subscription = Subscriptions.topics(topic)
  val vSource = Consumer.plainSource(consumerSettings, subscription)


  /*
  val consumer  = Consumer.plainSource(consumerSettings, subscription).map { ele =>
    println(s"${ele.timestamp()} __ ${ele.value()}")
    val in1 = vSource
    val in2 = Source.fromIterator(() => Seq( ele.value()).toIterator )
    Source.combine(in1, in2)(Concat(_))
  }.runWith(Sink.ignore)
*/
  def fromBinary(bytes: Array[Byte])  = {
    val input = AvroInputStream.binary[Variant](bytes)
    val result = input.iterator.toSeq
  }

  def getFilteredVariants( uuid: UUID, start : Int, end : Int, filters : Seq[VariantFilter] ) : Future[VariantList] ={

    def buildFilter( v : Variant) : Boolean = ! filters.map(f => f.applyFilter(v) ).contains(false)

    vSource.map{ ele =>
      //val j =  ele.value.asInstanceOf[Variant]
      val j = "" //fromBinary(ele.value())
      println(s"key : ${ele.key()} ts: ${ele.timestamp()} value: ${ele.value()} offset: ${ele.offset()} result: ${j}")
    }.runWith(Sink.ignore)
/*
    val sumSink = Sink.fold[Int, Int](0)(_ + _)
    val variantCount : Flow[Variant,Int,NotUsed] = Flow[Variant].map(_ => 1)

    val counterRG: RunnableGraph[Future[Int]] = vSource.filter(buildFilter).via(variantCount).toMat(sumSink)(Keep.right)
    val counterFilterRG: RunnableGraph[Future[Int]] = vSource.filterNot(buildFilter).via(variantCount).toMat(sumSink)(Keep.right)

    val v2Seq: Flow[Variant, Seq[Variant], NotUsed] = Flow[Variant].filter(buildFilter).fold(Seq[Variant]())((vL, v) => vL ++ Seq(v))
    val vPackStartStop = vSource.via(v2Seq).map(x => x.view(start, end).toSeq)

    for {
      v <- vPackStartStop.runWith(Sink.head)
      cOk <- counterRG.run()
      cFilter <- counterFilterRG.run()
    } yield VariantList(v, cOk, cFilter)
*/
    println("some stream filtering")
    Future { VariantList(Seq(), 0 ,0 ) }
  }


}

*/