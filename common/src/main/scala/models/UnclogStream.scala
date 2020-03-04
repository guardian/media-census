package models

import vidispine.{VSLazyItem, VSFile}

case class UnclogStream (VSFile:VSFile, VSItem:VSLazyItem, ParentProjects:Seq[PlutoProject])