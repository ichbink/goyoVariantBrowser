package  variantBrowser


import org.scalajs.dom
import variantBrowser.components.VariantBrowserClient
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import japgolly.scalajs.react.extra.router.StaticDsl.Route
import japgolly.scalajs.react.{Callback, ReactDOM}
import japgolly.scalajs.react.extra.router._

@JSExport
object VariantBrowser extends js.JSApp{

  val baseUrl = BaseUrl("http://bioinfo01-ingemm.salud.madrid.org:9000")
  // Define the locations (pages) used in this application
  sealed trait Loc
  case class Home() extends Loc

  val routerConfig = RouterConfigDsl[Loc].buildConfig { dsl =>
    import dsl._

  ( emptyRule
    | dynamicRouteCT( ("/").caseClass[Home] ) ~> dynRenderR( (page,ctl) => VariantBrowserClient(ctl, page))
    ).notFound(redirectToPath(root)(Redirect.Replace))
  }

  @JSExport
  def main() : Unit = {
    dom.console.info(s"Initializing scalajs VariantBrowser lab")
    val router = Router( baseUrl, routerConfig.logToConsole)
    ReactDOM.render( router(), dom.document.getElementById("wrapper"))
  }
}


