
import java.net.URLClassLoader
import java.security.PrivilegedExceptionAction
import javax.inject._

import common.Transformers.{avroByteArrayToEvent, eventToDatapoint, _}
import de.zalando.play.controllers.PlayBodyParsing._
import it.gov.daf.common.authentication.Authentication
import org.apache.hadoop.security.UserGroupInformation
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.spark.opentsdb.OpenTSDBContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.pac4j.play.store.PlaySessionStore
import play.api.i18n.MessagesApi
import play.api.inject.{ApplicationLifecycle, ConfigurationProvider}
import play.api.mvc.{AnyContent, Request}
import play.api.{Configuration, Environment, Mode}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
 * This controller is re-generated after each change in the specification.
 * Please only place your hand-written code between appropriate comments in the body of the controller.
 */

package iot_ingestion_manager.yaml {
    // ----- Start of unmanaged code area for package Iot_ingestion_managerYaml
    
  @SuppressWarnings(
    Array(
      "org.wartremover.warts.Throw",
      "org.wartremover.warts.While",
      "org.wartremover.warts.Var",
      "org.wartremover.warts.Null"
    )
  )
    // ----- End of unmanaged code area for package Iot_ingestion_managerYaml
    class Iot_ingestion_managerYaml @Inject() (
        // ----- Start of unmanaged code area for injections Iot_ingestion_managerYaml
                                             val environment: Environment,
                                             val configuration: Configuration,
                                             val playSessionStore: PlaySessionStore,
        // ----- End of unmanaged code area for injections Iot_ingestion_managerYaml
        val messagesApi: MessagesApi,
        lifecycle: ApplicationLifecycle,
        config: ConfigurationProvider
    ) extends Iot_ingestion_managerYamlBase {
        // ----- Start of unmanaged code area for constructor Iot_ingestion_managerYaml

    Authentication(configuration, playSessionStore)

    @tailrec
    private def addClassPathJars(sparkContext: SparkContext, classLoader: ClassLoader): Unit = {
      classLoader match {
        case urlClassLoader: URLClassLoader =>
          urlClassLoader.getURLs.foreach { classPathUrl =>
            if (classPathUrl.toExternalForm.endsWith(".jar") && !classPathUrl.toExternalForm.contains("test-interface")) {
              sparkContext.addJar(classPathUrl.toExternalForm)
            }
          }
        case _ =>
      }
      if (classLoader.getParent != null) {
        addClassPathJars(sparkContext, classLoader.getParent)
      }
    }

    //given a class it returns the jar (in the classpath) containing that class
    private def getJar(klass: Class[_]): String = {
      val codeSource = klass.getProtectionDomain.getCodeSource
      codeSource.getLocation.getPath
    }

    private val uberJarLocation: String = getJar(this.getClass)

    private val conf = if (environment.mode != Mode.Test)
      new SparkConf().
        setMaster("yarn-client").
        setAppName("iot-ingestion-manager").
        setJars(List(uberJarLocation)).
        set("spark.yarn.jars", "local:/opt/cloudera/parcels/SPARK2/lib/spark2/jars/*").
        set("spark.serializer", "org.apache.spark.serializer.KryoSerializer").
        set("spark.io.compression.codec", "lzf").
        set("spark.speculation", "false").
        set("spark.shuffle.manager", "sort").
        set("spark.shuffle.service.enabled", "true").
        set("spark.dynamicAllocation.enabled", "true").
        set("spark.dynamicAllocation.minExecutors", "4").
        set("spark.dynamicAllocation.initialExecutors", "4").
        set("spark.executor.cores", Integer.toString(2)).
        set("spark.executor.memory", "2048m").
        set("spark.executor.extraJavaOptions", "-Djava.security.auth.login.config=/tmp/jaas.conf")
    else
      new SparkConf().
        setMaster("local").
        setAppName("iot-ingestion-manager")

    private def thread(block: => Unit): Thread = {
      val thread = new Thread {
        override def run(): Unit = block
      }
      thread.start()
      thread
    }

    private def HadoopDoAsAction[T](request: Request[AnyContent])(block: => T): T = {
      val profiles = Authentication.getProfiles(request)
      val user = profiles.headOption.map(_.getId).getOrElse("anonymous")
      val ugi = UserGroupInformation.createProxyUser(user, proxyUser)
      ugi.doAs(new PrivilegedExceptionAction[T]() {
        override def run: T = block
      })
    }

    UserGroupInformation.loginUserFromSubject(null)

    private val proxyUser = UserGroupInformation.getCurrentUser

    private var jobThread: Option[Thread] = None

    private var sparkSession: Try[SparkSession] = Failure[SparkSession](new RuntimeException())

    private var streamingContext: Try[StreamingContext] = Failure[StreamingContext](new RuntimeException())

    private var stopFlag = true

    private var openTSDBContext: Try[OpenTSDBContext] = Failure[OpenTSDBContext](new RuntimeException())

        // ----- End of unmanaged code area for constructor Iot_ingestion_managerYaml
        val start = startAction {  _ =>  
            // ----- Start of unmanaged code area for action  Iot_ingestion_managerYaml.start
            HadoopDoAsAction(current_request_for_startAction) {
        synchronized {
          jobThread = Some(thread {
            sparkSession match {
              case Failure(_) =>
                sparkSession = Try {
                  val ss = SparkSession.builder().config(conf).getOrCreate()
                  addClassPathJars(ss.sparkContext, getClass.getClassLoader)
                  ss
                }

                streamingContext = Try {
                  val ssc = sparkSession.map(ss => new StreamingContext(ss.sparkContext, Milliseconds(500)))
                  ssc
                }.flatten

                openTSDBContext = sparkSession.map(new OpenTSDBContext(_))

                val brokers = configuration.getString("bootstrap.servers").getOrElse(throw new RuntimeException)

                val groupId = configuration.getString("group.id").getOrElse(throw new RuntimeException)

                val topics = Set(configuration.getString("topic").getOrElse(throw new RuntimeException))

                val kafkaParams = Map[String, AnyRef](
                  "bootstrap.servers" -> brokers,
                  "key.deserializer" -> classOf[ByteArrayDeserializer],
                  "value.deserializer" -> classOf[ByteArrayDeserializer],
                  "enable.auto.commit" -> (false: java.lang.Boolean),
                  "group.id" -> groupId
                )
                stopFlag = false
                streamingContext foreach {
                  ssc =>
                    logger.info("About to create the stream")
                    KafkaUtils.
                      createDirectStream(ssc, PreferConsistent, Subscribe[Array[Byte], Array[Byte]](topics, kafkaParams)).
                      flatMap(cr => {
                        val dp = (avroByteArrayToEvent >>>> eventToDatapoint) (cr.value)
                        dp.toOption
                      }).
                      print()
                    //ssc.socketTextStream("master", 9999).print()
                    logger.info("Stream created")
                    logger.info("About to start the streaming context")
                    ssc.start()
                    logger.info("Streaming context started")
                    var isStopped = false
                    while (!isStopped) {
                      logger.info("Calling awaitTerminationOrTimeout")
                      isStopped = ssc.awaitTerminationOrTimeout(1000)
                      if (isStopped)
                        logger.info("Confirmed! The streaming context is stopped. Exiting application...")
                      else
                        logger.info("The streaming context is still active. Timeout...")
                      if (!isStopped && stopFlag) {
                        logger.info("Stopping the streaming context right now")
                        ssc.stop(true, true)
                        logger.info("The streaming context is stopped!!!!!!!")
                      }
                    }
                }
              case Success(_) => ()
            }
          })
          Start200("Ok")
        }
      }
            // ----- End of unmanaged code area for action  Iot_ingestion_managerYaml.start
        }
        val stop = stopAction {  _ =>  
            // ----- Start of unmanaged code area for action  Iot_ingestion_managerYaml.stop
            HadoopDoAsAction(current_request_for_startAction) {
        synchronized {
          sparkSession match {
            case Failure(_) => ()
            case Success(ss) =>
              logger.info("About to stop the streaming context")
              stopFlag = true
              jobThread.foreach(_.join())
              logger.info("Thread stopped")
              sparkSession = Failure[SparkSession](new RuntimeException())
          }
          Stop200("Ok")
        }
      }
            // ----- End of unmanaged code area for action  Iot_ingestion_managerYaml.stop
        }
    
    }
}