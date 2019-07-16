package mfmodels

import vidispine.VSEntry

object ItemStatus extends Enumeration {
  val FileNotAttached, NoArchivePathSet, ArchivePathNotValid, FileArchived = Value
}

case class ItemReport (status:ItemStatus.Value,entry:VSEntry)
