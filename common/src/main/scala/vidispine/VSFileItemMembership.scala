package vidispine

import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

case class VSFileItemMembership (itemId:String, shapes:Seq[VSFileShapeMembership])

case class VSFileShapeMembership(shapeId:String,componentId:Seq[String])

object VSFileItemMembership {
  def fromXml(node:NodeSeq):Try[VSFileItemMembership] = Try {
    new VSFileItemMembership(
      (node \ "id").text,
      (node \ "shape").map(shapeNode=>VSFileShapeMembership.fromXml(shapeNode) match {
        case Success(result)=>result
        case Failure(err)=>throw err
      })
    )
  }

  def fromMap(values:Map[String,AnyRef]) =
    new VSFileItemMembership(values("itemId").toString, values("shapes").asInstanceOf[Seq[Map[String,AnyRef]]].map(VSFileShapeMembership.fromMap))
}

object VSFileShapeMembership {
  def fromXml(node:NodeSeq):Try[VSFileShapeMembership] = Try {
    new VSFileShapeMembership(
      (node \ "id").text,
      (node \ "component").map(componentNode=>(componentNode \ "id").text)
    )
  }

  def fromMap(values:Map[String,AnyRef]) = new VSFileShapeMembership(values("shapeId").toString, values("componentId").asInstanceOf[Seq[String]])
}