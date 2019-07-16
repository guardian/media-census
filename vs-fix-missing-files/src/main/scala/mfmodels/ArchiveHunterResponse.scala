package mfmodels

import java.time.ZonedDateTime

/**
  * {u'entityClass': u'archiveentry',
  * u'entries': [{u'beenDeleted': False,
  * u'bucket': u'archivehunter-test-media',
  * u'etag': u'54dcb489cb40e89ef662009eaa9ad493-2',
  * u'file_extension': u'mp4',
  * u'id': u'YXJjaGl2ZWh1bnRlci10ZXN0LW1lZGlhOnZhY3V1bS5tcDQ=',
  * u'last_modified': u'2019-01-09T15:43:08Z',
  * u'lightboxEntries': [],
  * u'mediaMetadata': None,
  * u'mimeType': {u'major': u'video', u'minor': u'mp4'},
  * u'path': u'vacuum.mp4',
  * u'proxied': False,
  * u'region': u'eu-west-1',
  * u'size': 12819947,
  * u'storageClass': u'STANDARD'}],
  * u'entryCount': 1,
  * u'status': u'ok'}
  */

case class MimeType(major:String, minor:String)
case class ArchiveHunterEntry(
                             beenDeleted:Boolean,
                             bucket:String,
                             etag: String,
                             file_extension:String,
                             id:String,
                             last_modified:ZonedDateTime,
                             mimeType:Option[MimeType],
                             path:String,
                             proxied:Boolean,
                             region:String,
                             size:Long,
                             storageClass:String
                             )

case class ArchiveHunterResponse(entityClass:String,
                                 entryCount:Int,
                                 status:String,
                                 entries:Seq[ArchiveHunterEntry]
                                )