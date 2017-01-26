package variantBrowser.components

import japgolly.scalajs.react.extra._
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import org.scalajs.dom

import variantBrowser.VariantBrowser.Loc

import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js.annotation.JSName

/**
  * Created by jcsilla on 14/12/16.
  */


object VariantBrowserClient {

  case class Props ()

  case class State ( vbPanels : Seq[ReactElement] = Seq(), current : Option[ReactElement] = None )

/*
  case class AddPanel() extends Listenable[ReactElement] {
    override def register(f: (ReactElement) => Callback) : CallbackTo[Callback] = {
      CallbackTo[Backend].
    }
  }

  object kk extends Listenable[ReactElement] {
    override def register(f: (ReactElement) => Callback) : CallbackTo[Callback] = {
      dom.console.log(s"Register invoked for kk Listenable ${f.toString()}")
      CallbackTo( Callback(f))
    }
  }



  object AddPanel extends Broadcaster[ReactElement] {
    def run ( p: ReactElement) = {
      dom.console.log( s"CompAPI addPanel invoked with ${listeners} : ${listeners.size} : ${listeners.head.toString}" )
      listeners.head(p).runNow()
      broadcast(p)
    }
  }

  object DelPanel extends Broadcaster[ReactElement] {
    def run ( p: ReactElement) = {
      dom.console.log( s"CompAPI addPanel invoked with ${listeners} : ${listeners.size} : ${listeners.head.toString}" )
      listeners.head(p).runNow()
      broadcast(p)
    }
  }

*/

  class Backend( $: BackendScope[Props, State]) extends OnUnmount {
    def mounted(props: Props) = Callback {
       //$.modState(s => s.copy(header = props.headers)).runNow
      dom.console.log("backend")
    }


    def render ( p: Props, s : State ) =
    {
      <.div(^.className := "variant-browser-client")(
        FileSelector( this.addVbPanel ),
        <.ul(^.className := "nav nav-tabs")(
          s.vbPanels.map { p =>
            s.current match {
              //todo set action to active tab when click
              case Some( x ) if x == p =>  <.li(^.className := "active")(  <.a( ^.href:="#")("Name of Panel" ))
              case default => <.li(  <.a( ^.href:="#")("Name of Panel not seleted"))
            }
          }
        ),
        s.current match {
          case Some(p) => p
          case None => <.div(^.className:="noContentYet")
        }
      )
    }

    def addVbPanel( p : ReactElement ) = Callback {
      dom.console.log( s"addVpPanel within Backend $p")
      $.modState( s =>  s.copy( vbPanels = s.vbPanels :+ p , current = Some(p))).runNow()
    }

    def delVpPanel( p: ReactElement ) = Callback {
      dom.console.log( s"delVpPanel within Backend $p")
      $.modState( s => s.copy ( vbPanels = s.vbPanels.filter( _ != p) )).runNow()
    }

  }

 // val fileSelector = FileSelector.apply

  private var c : Option[ReactElement] = None


  @JSName("VariantBrowserClient")
  val component = ReactComponentB[Props]("VariantBrowserClient")
    .initialState[State](State( ))  // ( SelectFile, ProgressMeter(), None, None, None,Seq(), Seq(), UUID.randomUUID() ) )
   // .backend(new Backend(_))
  //    .renderBackend
    .renderBackend[Backend]
    .componentDidMount( $ => $.backend.mounted( $.props) )
  //  .configure( Listenable.install( p => AddPanel , _.backend.addVbPanel),
  //              Listenable.install( p => DelPanel , _.backend.delVpPanel)
  //)
  .build

  def apply( r: RouterCtl[Loc],  page :  Loc) : ReactElement = {
    val x = component ( Props () )
    c = Some(x)
    x
  }

}



/*
  def addPanel ( p : ReactElement ) = {
    dom.console.log(s"some from FileSelector $p")
    CompAPI.addPanel(p)
    dom.console.log(s"ComAPI.addPanel invoked $p ")
  }
*/