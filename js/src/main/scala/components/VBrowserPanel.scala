package variantBrowser.components

import java.util.UUID

import japgolly.scalajs.react.extra.OnUnmount
import japgolly.scalajs.react._
import org.scalajs.dom
import services.AjaxClient
import shared.variants.HeaderField
import shared.variants.HeaderFieldCoor
import shared.variants.NoFilter
import shared.variants.VariantFilter
import shared.variants._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js.annotation.JSName

import autowire._
import services._
import boopickle.Default._
import scala.concurrent.Future
import shared.GoyoVariantBrowserAPI
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue


/**
  * Created by jcsilla on 27/12/16.
  */
object VBrowserPanel {

  trait Tab
  object Filtered extends Tab
  object Pass extends Tab
  val MAXPAGE : Int = 49

  case class Props( name: String, uuid: UUID,
                    //passTab : ReactElement, filterTab : ReactElement
                    chr: HeaderFieldCoor, start: HeaderFieldCoor, end: Option[HeaderFieldCoor],
                    headers : Seq[HeaderField]
                  )

  case class State( passTab : ReactElement = <.div(""), filterTab : ReactElement = <.div("") , activeTab: Tab = Pass,
                    passNum : BigInt = 0, filterNum : BigInt = 0 )

  class Backend( $: BackendScope[Props, State]) extends OnUnmount {

    def mounted ( p : Props) = applyVariantFilter(Seq[VariantFilter](NoFilter()),Tuple2(0,MAXPAGE), Tuple2(0,MAXPAGE) )

    def activeTab( x : Tab )(e :ReactEvent) =  {
      e.preventDefault()
      x match {
        case Pass =>  $.modState(s => s.copy( activeTab = Pass))
        case Filtered => $.modState(s => s.copy( activeTab = Filtered))
        case default => $.modState(s => s.copy( activeTab = Pass))
      }
    }

    def render ( s: State, p: Props) = {
      s.activeTab match {
        case Pass =>
          <.div (
            <.ul( ^.className:= "nav nav-tabs") (
              <.li(^.className:= "active")( <.a( ^.href:="", ^.onClick ==> activeTab(Pass) )(s"PASS : ${s.passNum}") ),
              <.li( <.a(^.href:="",  ^.onClick ==> activeTab(Filtered) )(s"FILTER : ${s.filterNum}") )
            ),
            <.div(^.className := "panel-content")( s.passTab )
          )
        case Filtered =>
          <.div (
            <.ul( ^.className:= "nav nav-tabs") (
              <.li( <.a( ^.href:="", ^.onClick ==> activeTab(Pass) )(s"PASS : ${s.passNum}") ),
              <.li(^.className:= "active")(<.a(^.href:="",  ^.onClick ==> activeTab(Filtered) )(s"FILTER : ${s.filterNum}"))
            ),
            <.div(^.className := "panel-content")( s.filterTab )
          )
      }

    }

    def setTabsFromVariantList(props : Props, v : VariantList, filters: Seq[VariantFilter]) = $.modState(s => s.copy(
          passTab = FilterTab(this.applyVariantFilter, props.chr, props.start, props.end, props.headers, v.vPass, v.numVPass, filters),
          filterTab = FilterTab(this.applyVariantFilter, props.chr, props.start, props.end, props.headers, v.vFilter, v.numVFilter, Seq[VariantFilter]()),
          activeTab = Pass,
          passNum = v.numVPass,
          filterNum = v.numVFilter
        )
    )


    def applyVariantFilter( filters: Seq[VariantFilter], passPage : Tuple2[Int,Int], filterPage : Tuple2[Int,Int]) : Callback = Callback {
      val props = $.props.runNow()
      AjaxClient[GoyoVariantBrowserAPI].getVariants(props.uuid, filters : Seq[VariantFilter], passPage, filterPage).call.map { v =>
        dom.console.log(s"Reply from server $v")

        $.modState(s => s.copy(
              passTab = FilterTab(this.applyVariantFilter, props.chr, props.start, props.end, props.headers, v.vPass, v.numVPass, filters),
              filterTab = FilterTab(this.applyVariantFilter, props.chr, props.start, props.end, props.headers, v.vFilter, v.numVFilter, Seq[VariantFilter]()),
              activeTab = Pass,
              passNum = v.numVPass,
              filterNum = v.numVFilter
            )
        ).runNow()




        //setTabsFromVariantList(props, v, filters)
      }
    }
  }

  @JSName("VBrowserPanel")
  val component = ReactComponentB[Props]("FilterTab")
    .initialState[State]( State ())
    .renderBackend[Backend]
    .componentDidMount( scope => scope.backend.mounted(scope.props) )
    .build


  def apply( n: String, t: UUID,
             c: HeaderFieldCoor, s: HeaderFieldCoor, e : Option[HeaderFieldCoor],
             h : Seq[HeaderField]) : ReactElement = {
    component(
      Props ( n, t, c, s, e, h)
    )
  }

}
