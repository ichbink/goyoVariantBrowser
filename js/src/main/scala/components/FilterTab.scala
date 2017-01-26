package variantBrowser.components

import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.jquery._
import services._
import boopickle.Default._
import scala.concurrent.Future
import shared.GoyoVariantBrowserAPI
import shared.variants.HeaderField
import shared.variants.HeaderFieldCoor
import shared.variants.Variant
import shared.variants._

import scala.scalajs.js.annotation.JSName
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
  * Created by jcsilla on 7/12/16.
  */
object FilterTab {
  case class State(
                   header : Seq[HeaderField] = Seq[HeaderField](),
                   variants : Seq[Variant] = Seq(),
                   filters : Seq[VariantFilter] = Seq[VariantFilter](),
                   currentPage : Int = 0,
                   numVariants : Int = 0
  )

  case class Props(applyFilter  : (Seq[VariantFilter], Tuple2[Int,Int], Tuple2[Int,Int]) => Callback,
                   chr: HeaderFieldCoor, start: HeaderFieldCoor, end: Option[HeaderFieldCoor],
                   headers : Seq[HeaderField],
                   variants : Seq[Variant] = Seq(),
                   numVariants : Int = 0,
                   filters : Seq[VariantFilter] = Seq[VariantFilter]()
                  )

  val filters = collection.mutable.Set[VariantFilter]( NoFilter() )

  class Backend ( $: BackendScope[Props, State]) {
    def mounted(props: Props) =  $.modState( s =>
       s.copy( header =  props.headers, filters = props.filters, numVariants = props.numVariants )
    )

    def removeRedundantFilters( f : VariantFilter , filters : Set[VariantFilter]) : Set[VariantFilter] ={
      val  nFilters  = filters.filter( vf =>
        vf match {
          case ArbitraryFilter(x, y) =>
            f match {
              case ArbitraryFilter(j, k) if y == k && j.length != 0  =>
                (  j.length >= x.length &&  ! j.contains(x) ) ||  ( j.length < x.length && ! x.contains(j))
              case ArbitraryFilter(j, k) if y == k && j.length == 0 => false  //delete all filters for k index
              case default => true
            }
          case NumericalLowerEqualFilter(x,y) =>
            f match {
              case NumericalLowerEqualFilter(j,k) if y == k  && j.length != 0 =>
                (  j.length >= x.length &&  ! j.contains(x) ) ||  ( j.length < x.length && ! x.contains(j))
              case NumericalLowerEqualFilter(j,k) if y == k  && j.length == 0 => false
              case default => true
            }
          case FilterByChr(x) =>
            f match {
              case FilterByChr(j) if j.length != 0 =>
                (j.length >= x.length && ! j.contains(x)) || (j.length < x.length && ! x.contains(j))
              case FilterByChr(j) if j.length == 0 => false
              case default => true
            }
          case default => true
        }
      )

      f match {
        case ArbitraryFilter(j, k) if j.length != 0 => nFilters + f
        case ArbitraryFilter(j, k) if j.length == 0 => nFilters
        case NumericalLowerEqualFilter(j, k) if j.length != 0 => nFilters + f
        case NumericalLowerEqualFilter(j, k) if j.length == 0 => nFilters
        case FilterByChr(x) if x.length != 0 => nFilters + f
        case FilterByChr(x) if x.length == 0 => nFilters
        case default => nFilters + f
      }

    }


//    def setVariants ( v : Seq[Variant], n: Int) = $.modState(s =>s.copy( variants = v, numVariants = n ))

    def setVariantFilter (  f : VariantFilterObject)(e: ReactEventI) = Callback {
      dom.console.log("set Variant Filter")
    }

    def setVariantFilter (f : VariantFilterObject, pos: Int)(e: ReactEventI) = Callback {
      dom.console.log(s"set variant Filter to $pos")
      val state = $.state.runNow()
      val newFilter = f.getFilter(e.target.value)(pos)
      dom.console.log( s"filters before Redundant: $filters")
      val nFilters = removeRedundantFilters( newFilter, filters.toSet )
      filters.clear()
      nFilters.foreach( f =>  filters.add(f))

      dom.console.log( s"filters after Redundant: $filters")

      val props = $.props.runNow()
      props.applyFilter( filters.toSeq ,Tuple2(0,49),Tuple2(0,49)).runNow()

      //getVariants(state.vBrowser.uuid, 0, varPerPage -1,filters.toSeq)

    }

    def toggleHeaderFilters( label : String)(e :ReactEventI) = Callback {
      val ele = jQuery( e.target.parentNode).find( s""" ul[aria-labelledBy="${label}"]""" )
      ele.hasClass("hide") match {
        case true =>
          ele.removeClass("hide")
          ele.addClass("show")
          ele.attr("style" , "float:left")
        case false =>
          ele.removeClass("show")
          ele.addClass("hide")
      }
    }

    def header( props: Props, state : State) = {
      //todo show filters disabled when props.filter = false
      <.tr()(
        // chromosome
        <.th()(
          props.chr.value, props.chr.filtersAvailable.map { f =>
            <.div(^.className:="input-group")(
              <.span(^.className:="input-group-addon")(f.name),
              <.input(^.className := "form-control filterText",^.`type`:="text", ^.onChange ==> setVariantFilter(f,props.chr.pos) )
            )
          }
        ),
        // start
        <.th()(
          props.start.value, props.start.filtersAvailable.map { f =>
            <.div(^.className:="input-group")(
              <.span(^.className:="input-group-addon")(f.name),
              <.input(^.className := "form-control filterText",^.`type`:="text", ^.onChange ==> setVariantFilter(f, props.start.pos) )
            )
          }
        ),
        // optional end
        props.end match {
          case Some(x) =>
            <.th()(x.value, x.filtersAvailable.map { f =>
              <.div(^.className := "input-group")(
                <.span(^.className := "input-group-addon")(f.name),
                <.input(^.className := "form-control filterText", ^.`type` := "text", ^.onChange ==> setVariantFilter(f))
              )
            }
            )
          case None =>  <.th()("End")
        },
        // Another headers
        state.header.filter(  h => h.imp && h.visible ).map { h =>
          <.th(^.className:="")(
            <.div(^.className:="btn-group", "role".reactAttr := "group")(
              <.span(^.className:="btn btn-default btn-xs") (h.value),
              <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-eye-close",^.`type`:="button"), //( <.i(^.className:="glyphicon glyphicon-eye-close")),
              <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-sort",^.`type`:="button"), //( <.i(^.className:="fa fa-sort")),
              <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-filter",^.`type`:="button",
                ^.id :=s"filter-dropdown-${h.pos}", ^.onClick ==> toggleHeaderFilters( s"filter-dropdown-${h.pos}") ),
              <.ul(^.className:="list-group filters hide", "aria-labelledby".reactAttr := s"filter-dropdown-${h.pos}")(
                h.filtersAvailable.map { f =>
                  <.li(^.className:="list-group-item") (
                    <.div(^.className:="input-group input-group-xs")(
                      <.span(^.className:="input-group-addon")(f.name),
                      <.input(^.className := "form-control filterText",^.`type`:="text", ^.onChange ==> setVariantFilter(f,h.pos -3) )
                    )
                  )
                }
              )
            )
          )
        }
      )
    }

    def rows( props: Props, state : State ) = {
      props.variants.map { v =>
        <.tr(
          //Chr, Start, End
          <.td(v.coord.chr), <.td(v.coord.start.toString()), <.td(v.coord.end.getOrElse(".").toString),
          state.header.filter(j => j.imp && j.visible).map { h =>
            v.fieldSet.isDefinedAt(h.pos - 3) match {
              case true => <.td(^.className := "col-md-3")(v.fieldSet(h.pos - 3).value)
              case false => <.td(^.className := "col-md-3")(s"${h.pos - 3} not defined ${v.fieldSet}")
            }
          }
        )
      }
    }

    def pageIntervals( total : Int , start: Int) : Seq[Tuple2[Int,Int]] = {
      val MAXPAGE : Int = 49
      if( total <= MAXPAGE && start == 1) {
        Seq()
      }
      else {
        total <= MAXPAGE match {
          case true => Seq ( Tuple2( start, start + total) )
          case false => Seq( Tuple2( start, start + MAXPAGE -1 )) ++ pageIntervals( total - MAXPAGE, start + MAXPAGE)
        }
      }
    }

    def pageNav(p: Props, s : State) = {
      val intervals = pageIntervals(s.numVariants, 1)
      <.nav(^.className:="panel panel-default navbar-fixed-bottom") (
        <.ul(^.className:="pagination")(
          for (i <- intervals) yield{
            i match {
              case x if  x == intervals.head =>
                val pag = Tuple2( x._1,x._2)
                <.li( <.span(<.i( ^.className:="glyphicon glyphicon-circle-arrow-left", ^.onClick -->  p.applyFilter(s.filters, pag, pag)) ))
              case x if x == intervals.last && intervals.size != 1 =>
                val pag = Tuple2( x._1,x._2)
                <.li( <.span( <.i( ^.className:="glyphicon glyphicon-circle-arrow-right",  ^.onClick --> p.applyFilter(s.filters, pag, pag)) ))
              case default =>
                val pag = Tuple2( default._1,default._2)
                <.li( <.span(<.i(^.className:="glyphicon glyphicon-unchecked",  ^.onClick --> p.applyFilter(s.filters, pag, pag)) ))
            }
          }
        )
      )
    }

/*

    def gotoNavegation( uuid :  UUID, start: Int, end : Int)(e:  ReactEventI) = Callback {
      dom.console.log(s"get variants for ${uuid} start : ${start} end : ${end}")
      getVariants(uuid, start, end, filters.toSeq )
    }

*/
    def render(props: Props, state : State) = {
     //  dom.console.log(s"render of FilterTab : ${state.header.size}")
      <.div(^.className:="table-responsive")(
        pageNav(props, state),
        <.table(^.id:="entityList", ^.className:="table table-hover table-bordered") (
          <.thead()( header( props, state) ),
          <.tbody()(rows(props, state) )
        )
      )
    }
  }

  @JSName("FilterTab")
  val component = ReactComponentB[Props]("FilterTab")
    .initialState[State]( State ())
    .renderBackend[Backend]
    .componentDidMount( scope => scope.backend.mounted(scope.props) )
    .build

  def apply( fn : ( Seq[VariantFilter], Tuple2[Int,Int], Tuple2[Int,Int] ) => Callback,
             c: HeaderFieldCoor, s: HeaderFieldCoor, e : Option[HeaderFieldCoor], h : Seq[HeaderField],
             vars : Seq[Variant], numVars : Int, f: Seq[VariantFilter] ) : ReactElement = component( Props( fn,c,s,e,h,vars, numVars, f))

}
