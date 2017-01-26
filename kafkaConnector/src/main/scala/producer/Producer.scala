package kafkaConnector.producer

import java.io.{ByteArrayInputStream, InputStream, File, ByteArrayOutputStream}
import java.lang.Thread.UncaughtExceptionHandler
import java.nio.ByteBuffer
import java.util
import java.util.logging.Logger
import java.util.{Collections, UUID, Properties}

import kafka.admin.AdminUtils
import kafka.cluster.Partition
import kafka.consumer.ConsumerThreadId
import kafka.javaapi.consumer.ConsumerRebalanceListener
import kafka.server.KafkaConfig
import kafka.utils.ZkUtils

import org.I0Itec.zkclient.{ZkConnection, ZkClient}
import org.apache.avro.Schema.Parser
import org.apache.avro.file.{DataFileWriter, DataFileReader}
import org.apache.avro.generic.GenericData.Record
import org.apache.avro.generic._
import org.apache.avro.io.{EncoderFactory, BinaryEncoder}
import org.apache.avro.specific.{SpecificDatumWriter, SpecificData}
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.{TopicPartition, PartitionInfo}
import org.apache.kafka.streams.state.KeyValueStore
import shared.variants.Variant
import shared.variants.VariantFilter
import shared.variants.VariantList
import shared.variants._
import scala.io.Source
import scala.util.Random


//import akka.actor.{ActorSystem}
import org.apache.avro.{SchemaBuilder, Schema}
import org.apache.kafka.clients.consumer.{InvalidOffsetException, ConsumerRecord, KafkaConsumer, ConsumerConfig}

import org.apache.kafka.streams.processor._
import org.apache.kafka.streams.kstream._
import org.apache.kafka.streams.{KafkaStreams, KeyValue, StreamsConfig}

import org.apache.kafka.common.serialization.Serdes

import scala.collection.mutable
import scala.concurrent.Future
import scala.reflect.runtime.{universe => ru}

import scala.collection.convert.wrapAll._

import com.sksamuel.avro4s._
import shared._

/*
class MyAvroSerde[T] extends Serde[T] {
  //val inner =  Serdes.serdeFrom[T]()


  override def deserializer(): Deserializer[T] = ???

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = ???

  override def close(): Unit = ???

  override def serializer(): Serializer[T] = ???
}

*/

//import org.apache.kafka.common.serialization.{StringSerializer, ByteBufferSerializer}
import boopickle.Default._


//import kafkaConnector.utils._
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord, KafkaProducer}


object VariantStream {

  private val topicPrefix = "variants"
  private val bootstrapServers  = Seq(
    "bioinfo01-ingemm:9092"
    //"bioinfo01-ingemm.salud.madrid.org:9092","bioinfo02-ingemm.salud.madrid.org:9092",
    //"bioinfo03-ingemm.salud.madrid.org:9092"
  )

  private val zooKeeperServers = Seq(
    "hd01-ingemm:2181"
    ,"hd01-ingemm.salud.madrid.org:2181","bioinfo01-ingemm.salud.madrid.org:2181","bioinfo02-ingemm.salud.madrid.org:2181",
    "bioinfo03-ingemm.salud.madrid.org:2181","hd01-ingemm.salud.madrid.org:2181",
    "hd02-ingemm.salud.madrid.org:2181"
  )

  private val producerConfig =  {
    val settings = new Properties()
    settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.mkString(","))
    settings.put(ProducerConfig.ACKS_CONFIG, "all")
    settings.put(ProducerConfig.RETRIES_CONFIG, "0")
    settings.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    settings.put(ProducerConfig.LINGER_MS_CONFIG,"1")
    settings.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")
    settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteBufferSerializer")

    settings
  }

 // io.confluent.kafka.serializers.KafkaAvroSerializer
  private val producerConfigAvro =  {
    val settings = new Properties()
    settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.mkString(","))
    settings.put(ProducerConfig.ACKS_CONFIG, "all")
    settings.put(ProducerConfig.RETRIES_CONFIG, "0")
    settings.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384")
    settings.put(ProducerConfig.LINGER_MS_CONFIG,"1")
    settings.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432")

   settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
   settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer")

   // settings.put("schema.registry.url","http://localhost:8081")

   // settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "io.confluent.kafka.serializers.KafkaAvroSerializer")
   // settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"io.confluent.kafka.serializers.KafkaAvroSerializer")

    settings
  }


  private val streamConfig = {
    val settings = new Properties()
    settings.put(StreamsConfig.CLIENT_ID_CONFIG, "goyo-variant-browser-client")
    settings.put(StreamsConfig.APPLICATION_ID_CONFIG, "goyo-variant-browser")
    settings.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers.mkString(",") )
    settings.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG,zooKeeperServers.mkString(",") )
    settings.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass.getName)
    settings.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, Serdes.ByteBuffer().getClass.getName)
  //  settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
    settings
  }


  private var uuid = ""

  private val zkClient = ZkUtils.createZkClient(zooKeeperServers.head,1000000,1000)


  /*
  private val schema = new Parser().parse(
 //   Source.fromFile("/home/jcsilla/desa/goyoVariantBrowser/kafkaConnector/src/main/avro/Variant/Variant.avsc").mkString
    Source.fromFile("/home/jcsilla/desa/goyoVariantBrowser/kafkaConnector/src/main/avro/Variant/TestCoord.avsc").mkString
  )
  private val writer = new GenericDatumWriter[GenericRecord](schema)
  */

  //private val producer = new KafkaProducer[String,Object]( producerConfig )
  private val producer = new KafkaProducer[String,Array[Byte]]( producerConfigAvro )

  private val stringSerde = Serdes.String()
  private val byteBufferSerde = Serdes.ByteBuffer()


  def send( event : Seq[Variant], uuId : UUID) = {
    val topic = s"${topicPrefix}_${uuId.toString}"

    if (zkClient.exists(ZkUtils.getTopicPath(topic) )== false) {
      uuid = uuId.toString
      println(s"Topic not exist, create : ${topic}")
      AdminUtils.createTopic(ZkUtils(zkClient,false),topic , 3, 2)
      println("Done!")
    }
    val variantSeq = VariantSeq(event)
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[VariantSeq](baos)

    output.write( variantSeq )
    output.close()

    val record = new ProducerRecord[String,Array[Byte]](topic,baos.toByteArray )

    try {
      val ack = producer.send( record )
//      println(s"Event sent to kafka: Partition: ${ack.get().partition()}\n\nOffset: ${ack.get().offset()}")
    }
    catch {
      case e : SerializationException => {
        println(s"SerializationException : ${e.getMessage}")
        e.printStackTrace()
      }
      case default : Any => println(s"AnotherException: ${default.getMessage}")
    }
  }



  def getFilteredVariants( uuid: UUID, filters : Seq[VariantFilter], passPage : Tuple2[Int,Int], filterPage : Tuple2[Int,Int] ) : Future[VariantList] = {
    println("GetFilteredVariants start")
    import scala.concurrent.ExecutionContext.Implicits.global
    val id = Random.alphanumeric.filter(_ != "?" ).take(6).mkString
    val consumerConfig = {
      val settings = new Properties( )
      settings.put(ConsumerConfig.CLIENT_ID_CONFIG, "goyo-variant-browser-client-" + id)
      settings.put(ConsumerConfig.GROUP_ID_CONFIG, "goyo-variant-browser-" + id )
      settings.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
      settings.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootstrapServers.mkString(","))
      settings.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest")
      settings.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
      settings.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteBufferDeserializer")
      settings
    }

    val variantConsumer = new KafkaConsumer[String, ByteBuffer]( consumerConfig )


    var variantCount = 0
    var filterPassCount = 0
    var filterFilteredCount = 0
    val topic = s"${topicPrefix}_${uuid.toString}"
    val vPass = mutable.ArrayBuffer[Variant]()
    val vFilter = mutable.ArrayBuffer[Variant]()
    // read Variants from kafta into a stream
    def buildFilter(v: Variant): Boolean = ! filters.map(f => f.applyFilter(v)).contains(false)

    try {

      val partitions = for ( i <- variantConsumer.partitionsFor(topic))  yield new TopicPartition( i.topic(), i.partition() )

      variantConsumer.assign(partitions)
        var again = true

        while (again) {
     //     println(s"partitions : ${p.toString()} Position :  ${variantConsumer.position(p)}")
          val records = variantConsumer.poll(500)
          records.map { r =>
            val in = new ByteArrayInputStream(r.value().array())
            val input = AvroInputStream.binary[VariantSeq](in)
            for ( vl <- input.iterator()) {
              vl.vars.map { v =>
                buildFilter(v) match {
                  case true =>
                    vPass += v
                    filterPassCount += 1
                  case false =>
                    vFilter += v
                    filterFilteredCount += 1
                }
                variantCount += 1
              }
            }
          }
          again = !records.isEmpty
        }
    }
    catch {
      case er : InvalidOffsetException  => print(s"invalid offset : ${er.getMessage}")
      case default: Throwable => print(s"Exception ${default.getMessage}")
    }
    finally {
      variantConsumer.close()
      println( s"( #variants , #pass , #filtered : ${variantCount} , ${filterPassCount} , ${filterFilteredCount} )" )
    }
    Future {
      VariantList(
        vPass.drop(passPage._1).take( math.min( passPage._2 - passPage._1, vPass.size +1) ), filterPassCount,
        vFilter.drop(passPage._1).take( math.min( filterPage._2 - filterPage._1, vFilter.size +1 )), filterFilteredCount
      )
    }
  }

  def stop = {
    println("Stopping kafka stream...")
    val topic = s"${topicPrefix}_${uuid}"
    try {
      //stream.close()
      println("stream closed...")
      //stream2.close()
      println("stream2 closed...")
    //  variantConsumer.unsubscribe()
    //  variantConsumer.close()
      println("Variant Consumer closed...")

      println(s"Delete topic : $topic")
      zkClient.deleteRecursive(ZkUtils.getTopicPath(topic) )
      println("Done")

      Future.successful { println("stop kafka streams") }

    } catch  {
      case default : Throwable =>
        println(s"failed to stop kafka streams ${default.getMessage}")
        Future.failed( default)
    }

  }

}




/*

  //Stream processing

  val builder : KStreamBuilder = new KStreamBuilder()

  //val variantSource : KStream[String,ByteBuffer] = builder.stream[String, ByteBuffer](topic)


  class MyProcessor extends Processor[String,ByteBuffer] with ProcessorSupplier[String, ByteBuffer] {

    private var context : ProcessorContext = null

    override def init(context: ProcessorContext): Unit = {
      this.context = context
      println("context processor init")
      this.context.schedule(1000)
     // state.put("totalCount", context.getStateStore("totalCount") )
    }

    override def punctuate(timestamp: Long): Unit = {
      println(s"punctuate ")
    }

    override def process(key: String, value: ByteBuffer): Unit = {
      println( s"variant processing from kafka-topic:${topicPrefix} =>  $value : ${vars.size}")
      //vars += value
      variantCount += 1
    }

    override def close(): Unit = {
      println("withing MyProcessor")
      println("close call on  MyProcessor")

    }

    override def get(): Processor[String, ByteBuffer] = this
  }

  def buildFilter( f: Seq[VariantFilter]) : Predicate[String,ByteBuffer] = new Predicate[String, ByteBuffer] {
    //println( "filter predicate :")
    override def test(key: String, value: ByteBuffer): Boolean = {
      //f.map( ff =>  ff.applyFilter( ) )
      //println (s"filter something from stream: ${value} : ${key}")
      true
    }
  }

  def action : ForeachAction[String,ByteBuffer] =  new ForeachAction[String,ByteBuffer]{
    override def apply(key: String, value: ByteBuffer): Unit = {
      println ( s"materialized value: $value :: --${key}-- ${vars.size}")
     // vars += value
    }
  }
*/

  /*
  def sumVars ( i : Long ) : ValueMapper[java.lang.Long,Long] = new ValueMapper[java.lang.Long, Long] {
    override def apply(value: java.lang.Long): Long = {
      println(s"apply sumVars")
      passVariantCount += 1
      value + i
    }
  }

  val variantFilterPass = variantSource.filter( buildFilter( filterList ))
    .countByKey("sumPass")
    .mapValues[Long](sumVars(0L)) //.to("variantsCountsPass")

  val variantFilterFiltered =  variantSource.filterNot(buildFilter(filterList))
    .countByKey("sumFilter")
    .mapValues[Long]( sumVars(0L)) //.to("variantCountFilter")

  */

  /*
  // Stream definition
  val stream = new KafkaStreams(builder, streamConfig)

  stream.setUncaughtExceptionHandler(
    new UncaughtExceptionHandler () {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        println( e.getMessage )
      }
    }
  )
  stream.start()



  val variantBuilder = new TopologyBuilder()
  variantBuilder.addSource("VariantSource","variants")
    .addProcessor("filterProcessor",new MyProcessor , "VariantSource")
    .addSink("Sink", "sink-topic", "filterProcessor")
  */
/*
  val stream2 = new KafkaStreams(variantBuilder, streamConfig)
  stream2.setUncaughtExceptionHandler(
    new UncaughtExceptionHandler () {
      override def uncaughtException(t: Thread, e: Throwable): Unit = {
        println(  e.getMessage )
        println("Exception on stream2 from topology builder")
      }
    }
  )

  stream2.start()

*/


/*
class myProducer[A](topic: String) {
  val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092")
  props.put("acks", "all")
  props.put("retries", "0")
  props.put("batch.size", "16384")
  props.put("linger.ms","1")
  props.put("buffer.memory", "33554432")
  props.put("key.serializer", "org.apache.kafka.common.serialization.ByteBufferSerializer")
  props.put("value.serializer","org.apache.kafka.common.serialization.ByteBufferSerializer")

  private lazy val producer = new KafkaProducer[A,ByteBuffer](props)

  def send(message: A) = {
    val record = new ProducerRecord[A,ByteBuffer](topic, message.asInstanceOf[ByteBuffer])
    producer.send(record )
  }

  def sendStream(stream: Stream[A]) = {
    val iter = stream.iterator
    while(iter.hasNext) {
      send(iter.next())
    }
  }

 // private def keyedMessage(topic: String, message: A): KeyedMessage[A, A] = new ProducerRecord[A, A](topic, message)
 // private def sendMessage(producer: KafkaProducer[A, A], message: KeyedMessage[A, A]) = producer.send(message)
}

class TestAkkProducer[A] ( system: ActorSystem, topic : String) {

  implicit val actorSystem = system
  implicit val materializer = ActorMaterializer()


/*
  implicit val schemaFor = SchemaFor[Variant]
  implicit val ssFor = SchemaFor[Coord]
  implicit val toRecord = ToRecord[Variant]

  val schema = AvroSchema[Variant]
  println ( schema.toString(true))
  println ( schemaFor.toString )
*/

  val producerSettings = ProducerSettings(system, new StringSerializer, new ByteBufferSerializer)
    .withBootstrapServers("localhost:9092")

/*
  def toBinary(event: Variant) : ByteBuffer = {
    val baos = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[Variant](baos)
    output.write(event)
    output.close()

    ByteBuffer.wrap(baos.toByteArray)
  }
*/

  def send( event: Variant, uuid : UUID) = {

  //  val record = new ProducerRecord[String, ByteBuffer]( topic , toBinary(event))
/*
    Source.fromIterator(
      () => Seq( record).toIterator
    ).runWith(Producer.plainSink(producerSettings) )
   */
  }

}

*/
