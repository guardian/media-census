package models

import vidispine.{VSLazyItem, VSFile}

case class UnclogStream (VSFile:VSFile, VSItem:Option[VSLazyItem], ParentProjects:Seq[PlutoProject])