package responses

case class ObjectGetResponse[T:io.circe.Encoder] (status:String, entityClass:String, entry:T)