package controllers

import akka.actor.ActorSystem
import akka.NotUsed
import akka.actor.{ActorRefFactory, ActorSystem}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer, SourceShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, RunnableGraph, Sink, Source}
import javax.inject.Inject
import play.api.{Configuration, Logger}
import play.api.inject.Injector
import play.api.libs.circe.Circe
import play.api.mvc.{AbstractController, ControllerComponents}
import streamComponents.{AssetSweeperFilesSource, FilterOutIgnores}
import io.circe.generic.auto._
import io.circe.syntax._
import models.AssetSweeperFile
import responses.ObjectListResponse

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class TestStreamController @Inject()(cc:ControllerComponents, config:Configuration, injector:Injector)(implicit system:ActorSystem) extends AbstractController(cc) with Circe {
  implicit val mat:ActorMaterializer = ActorMaterializer.create(system)
  private val logger = Logger(getClass)

  def testStream = Action.async {
    val sink = Sink.seq[AssetSweeperFile]

    val graph = GraphDSL.create(sink) { implicit builder=> { streamSink =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val srcFactory = injector.instanceOf(classOf[AssetSweeperFilesSource])
      val streamSource = builder.add(srcFactory)
      val ignoresFilter = builder.add(new FilterOutIgnores)
      streamSource ~> ignoresFilter ~> streamSink

      ClosedShape
    }}

    val resultFuture = RunnableGraph.fromGraph(graph).run()

    resultFuture.map(results=>{
      //if one of these is going to blow up circe, get a better error report here
      results.foreach(result=>{
        try {
          result.asJson
        } catch {
          case ex:Throwable=>
            logger.warn(s"$result failed to render: ", ex)
        }
      })
      Ok(ObjectListResponse("ok","AssetSweeperFile",results,results.length).asJson)
    })
  }
}
