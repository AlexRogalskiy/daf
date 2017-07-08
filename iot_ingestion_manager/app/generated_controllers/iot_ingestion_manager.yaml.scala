
import java.net.URLClassLoader
import javax.inject._

import de.zalando.play.controllers.PlayBodyParsing._
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import play.api.i18n.MessagesApi
import play.api.inject.{ApplicationLifecycle, ConfigurationProvider}
import play.api.{Environment, Mode}

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
      "org.wartremover.warts.Var"
    )
  )
    // ----- End of unmanaged code area for package Iot_ingestion_managerYaml
    class Iot_ingestion_managerYaml @Inject() (
        // ----- Start of unmanaged code area for injections Iot_ingestion_managerYaml
                                             environment: Environment,
        // ----- End of unmanaged code area for injections Iot_ingestion_managerYaml
        val messagesApi: MessagesApi,
        lifecycle: ApplicationLifecycle,
        config: ConfigurationProvider
    ) extends Iot_ingestion_managerYamlBase {
        // ----- Start of unmanaged code area for constructor Iot_ingestion_managerYaml
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
        set("spark.dynamicAllocation.minExecutors", "8").
        set("spark.dynamicAllocation.initialExecutors", "8").
        set("spark.executor.cores", Integer.toString(1)).
        set("spark.executor.memory", "2048m")
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

    var jobThread: Option[Thread] = None

    var sparkSession: Try[SparkSession] = Failure[SparkSession](new RuntimeException())

        // ----- End of unmanaged code area for constructor Iot_ingestion_managerYaml
        val start = startAction {  _ =>  
            // ----- Start of unmanaged code area for action  Iot_ingestion_managerYaml.start
            synchronized {
        jobThread = Some(thread {
          sparkSession match {
            case Failure(_) => sparkSession = Try {
              val sparkSession = SparkSession.builder().config(conf).getOrCreate()
              addClassPathJars(sparkSession.sparkContext, getClass.getClassLoader)
              sparkSession
            }
              while (!sparkSession.getOrElse(throw new RuntimeException).sparkContext.isStopped)
                Thread.sleep(100)
            case Success(ss) => ()
          }
        })
        Start200("Ok")
      }
            // ----- End of unmanaged code area for action  Iot_ingestion_managerYaml.start
        }
        val stop = stopAction {  _ =>  
            // ----- Start of unmanaged code area for action  Iot_ingestion_managerYaml.stop
            synchronized {
        sparkSession match {
          case Failure(ex) => ()
          case Success(ss) =>
            ss.stop()
            jobThread.foreach(_.join())
            sparkSession = Failure[SparkSession](new RuntimeException())
        }
        Stop200("Ok")
      }
            // ----- End of unmanaged code area for action  Iot_ingestion_managerYaml.stop
        }
    
    }
}
