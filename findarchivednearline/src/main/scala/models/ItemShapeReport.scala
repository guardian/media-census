package models

import vidispine.VSLazyItem

case class ItemShapeReport (vsItem:VSLazyItem, noOriginalShape:Boolean, onlineShapeCount:Int, nearlineShapeCount:Int, otherShapeCount:Int, fileSize:Option[Long])
