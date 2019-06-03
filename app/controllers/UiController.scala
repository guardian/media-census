package controllers

import java.util.Properties

import javax.inject.Inject
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.mvc.{AbstractController, ControllerComponents}

class UiController @Inject() (config:Configuration,cc:ControllerComponents) extends AbstractController(cc) {
  private val logger = LoggerFactory.getLogger(getClass)

  def index = Action {
    val cbVersionString = try {
      val prop = new Properties()
      prop.load(getClass.getClassLoader.getResourceAsStream("version.properties"))
      Option(prop.getProperty("build-sha"))
    } catch {
      case e:Throwable=>
        logger.warn("Could not get build-sha property: ", e)
        None
    }
    Ok(views.html.index("Media Census")(cbVersionString.getOrElse("no-cachebusting")))
  }
}
