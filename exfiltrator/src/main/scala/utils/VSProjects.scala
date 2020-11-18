package utils

import akka.actor.ActorSystem
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import com.gu.vidispineakka.streamcomponents.VSCollectionSearchSource
import com.gu.vidispineakka.vidispine.VSCommunicator

object VSProjects {
  def makeSearchDocSensitive() = {
    <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
      <field>
        <name>gnm_type</name>
        <value>project</value>
      </field>
      <field>
        <name>gnm_storage_rule_sensitive</name>
        <value>storage_rule_sensitive</value>
      </field>
    </ItemSearchDocument>.toString()
  }

  def findSensitiveProjectIds(implicit comm:VSCommunicator, mat:Materializer, system:ActorSystem) = {
    val sinkFact = Sink.seq[String]
    val graph = GraphDSL.create(sinkFact) {implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new VSCollectionSearchSource(Seq(),makeSearchDocSensitive(), false, 100))

      src.out.map(_.itemId) ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  def makeSearchDocDeletable() = {
    <ItemSearchDocument xmlns="http://xml.vidispine.com/schema/vidispine">
      <field>
        <name>gnm_type</name>
        <value>project</value>
      </field>
      <field>
        <name>gnm_storage_rule_deletable</name>
        <value>storage_rule_deletable</value>
      </field>
      <operator operation="NOT">
        <field>
          <name>gnm_storage_rule_deep_archive</name>
        </field>
      </operator>
    </ItemSearchDocument>.toString
  }

  def findDeletableProjectIds(implicit comm:VSCommunicator, mat:Materializer, system:ActorSystem) = {
    val sinkFact = Sink.seq[String]
    val graph = GraphDSL.create(sinkFact) {implicit builder=> sink=>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val src = builder.add(new VSCollectionSearchSource(Seq(), makeSearchDocDeletable(), false, 100))
      src.out.map(_.itemId) ~> sink
      ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }
}
