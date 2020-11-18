package streamComponents

import com.gu.vidispineakka.vidispine.{VSFile, VSLazyItem}

case class ExfiltratorStreamElement(file: VSFile, maybeItem: Option[VSLazyItem])
