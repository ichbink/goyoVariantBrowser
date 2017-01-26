package variantBrowser.components

import java.util.UUID
import autowire._
import diode.data.Pot
import diode.react.ModelProxy
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.ReactAttr
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import org.scalajs.dom
import org.scalajs.dom.raw._
import variantBrowser.VariantBrowser.{Home, Loc}
import shared.variants._
import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSName
import scala.scalajs.runtime.UndefinedBehaviorError
import services._
import shared._
import boopickle.Default._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.jquery.jQuery

/**
  * Created by jcsilla on 15/06/16.
  */
object FileSelector {

  trait FileSelectorState
  case object SelectFile extends FileSelectorState
  case object ChooseFields extends FileSelectorState
  case object ProcessingFile extends FileSelectorState
  case object Browsing extends FileSelectorState

  case class ProgressMeter( file : UndefOr[File] = js.undefined, bytesRead : BigInt = 0,
                            numVariant : BigInt = 0, finish : Boolean = false )

  //case class VBrowser(vList : VariantList = VariantList(Seq(),0,0), numVariants : Int = 0  , numFilteredVariants : Int = 0, uuid : UUID = UUID.randomUUID() )

  case class Props( sendEnd  : (ReactElement) => Callback ) //router: RouterCtl[Loc], page: Loc)

  case class State(state: FileSelectorState = SelectFile, progress : ProgressMeter = ProgressMeter(),
                   chr: Option[HeaderFieldCoor] = None , start: Option[HeaderFieldCoor] = None, end: Option[HeaderFieldCoor] = None,
                   header : Seq[HeaderField] = Seq[HeaderField](),
                   errors : Seq[String] = Seq[String](),
                   uuid : UUID = UUID.randomUUID()
                  )


  class Backend($: BackendScope[Props, State]) {

    var fileSize : BigInt = 0
    var file : UndefOr[File] = js.undefined
    var reader : FileReader = new FileReader

    var start : BigInt  = 0
    val blockSize = 50000
    var end : BigInt = blockSize - 1

 //   val filters = collection.mutable.Set[VariantFilter]( NoFilter() )
    var abort = false
    val bufferLines = scala.collection.mutable.ArrayBuffer[String]()
    var firstLine = true

    val varPerPage = 50

    def mounted(props: Props) = Callback.log("FileSelector")

    def getHeaders = ( e: dom.UIEvent)  =>  {
      reader.result.toString.split("\n")  match {
        case x : Array[String] if x.size > 0 =>
          val headers = x(0).split("\t")
          val chr = HeaderFieldCoor( 0,true, true, headers(0))
          val start = HeaderFieldCoor(1,true, true, headers(1))
          val end = HeaderFieldCoor(2,true, true, headers(2))
          val headerIndex = for ( i <- (3 until headers.size ) )
            yield { HeaderField( i, value = headers(i)) }

          $.modState( s =>
            s.copy( chr = Some(chr), start = Some(start), end = Some(end), header = headerIndex)
          ).runNow()
        case default => dom.console.error(s"Not new line char found in first ${blockSize} bytes")
      }
    }

    def getLinesFromChunk( s: String) : Seq[String] = {
      "\n".r.findFirstMatchIn(s) match {
        case Some(m) => bufferLines.isEmpty match {
          case true =>
            if (  firstLine ) {
              firstLine = false
              getLinesFromChunk(s.substring(m.end))
            }
            else {
              Seq (  s.substring(0, m.start) ) ++ getLinesFromChunk(s.substring(m.end))
            }
          case false =>
            val buffered =  bufferLines.mkString("")
            bufferLines.clear()
            if (  firstLine ) {
              firstLine = false
              getLinesFromChunk(s.substring(m.end))
            }
            else {
              Seq(buffered + s.substring(0, m.start)) ++ getLinesFromChunk(s.substring(m.end))
            }
        }
        case None =>
          bufferLines += s
          Seq()
      }
    }

    def processChunk = ( e: Any)  => {
      try {
        dom.console.log(s"start processChunk")

        val varBuffer = collection.mutable.Buffer[Variant]()
        val chunkContent = reader.result.toString
        val chunkSizeRead = reader.result.toString.getBytes("UTF-8").size

        dom.console.log(s"${chunkSizeRead} has been readed ")

        val lines = getLinesFromChunk(chunkContent)
        dom.console.log(s"${lines.size} lines has been readed ")

        val state = $.state.runNow()
        val vFilter = state.header.filter(_.imp == true).map(_.pos).sorted

        lines.map { vLine =>
          val fields = vLine.split("\t")
          try {
            varBuffer += Variant(
              Coord(
                fields(state.chr.get.pos),
                fields(state.start.get.pos).toInt,
                state.end match {
                  case None => None
                  case Some(x) => Some(fields(x.pos).toInt)
                }
              ),
              vFilter.map(fields(_)).map(SingleVariantField(_))
            )
         //   dom.console.log(s"${varBuffer.last}")
          }
          catch {
            case e: UndefinedBehaviorError =>
              $.modState(s => s.copy(errors = s.errors :+ e.getMessage)).runNow
              dom.console.error(s"error: ${e.printStackTrace()} :: ${e.getMessage}")
            case default: Throwable =>
              $.modState(s => s.copy(errors = s.errors :+ default.getLocalizedMessage)).runNow
              dom.console.error(s"error parsing file ${default}")
          }
        }

        dom.console.log("Before ajax call")

        AjaxClient[GoyoVariantBrowserAPI].persistVariants(state.uuid, varBuffer.toSeq).call.map { u =>
          dom.console.log(s"result with $u : ${varBuffer.size}")
          if (start < fileSize) {
            dom.console.log(s"call to next chunk ${state.state} ")
            $.modState(s => s.copy(
              progress = ProgressMeter(s.progress.file, s.progress.bytesRead + chunkSizeRead, s.progress.numVariant + u, false))
            ).runNow()

            readFileLines
            dom.console.log(s"Fetch more lines")

            /*
            state.state match  {
              case ProcessingFile if state.progress.numVariant > varPerPage =>
                dom.console.log(s"Start browsing while upload rest of file")
                $.modState( s => s.copy( state = Browsing ) ).runNow()
                getVariants(state.vBrowser.uuid,0,varPerPage -1, filters.toSeq)
            }
            */
          }
          else {
            dom.console.log("File processing complete")
            $.modState( s =>
              s.copy(state = Browsing,
                     progress = ProgressMeter(s.progress.file, s.progress.bytesRead + chunkSizeRead, s.progress.numVariant + u, true)
              )
            ).runNow()
            //c: HeaderFieldCoor, s: HeaderFieldCoor, e : Option[HeaderFieldCoor], h : Seq[HeaderField
            val s = $.state.runNow()

         //   VariantBrowserClient.AddPanel.run( VBrowserPanel(  "wv",s.uuid, s.chr.get, s.start.get, s.end, s.header ) )
            val p = $.props.runNow()
            p.sendEnd( VBrowserPanel(  "wv",s.uuid, s.chr.get, s.start.get, s.end, s.header ) ).runNow()
          }
        }

      }
      catch {
        case e: UndefinedBehaviorError =>
     //     dom.console.error(s"error: ${e.printStackTrace()} :: ${e.getMessage}")
          dom.console.error(s"wtf!!! Exception inside processChunk ${e.getMessage}")
      }
    }



    def resetFileSelector = {
      start = 0
      end = blockSize - 1
      abort = false
      firstLine = true
      $.modState( s => s.copy( header = Seq[HeaderField]())).runNow()
    }

    def readHeaders(e: SyntheticEvent[HTMLInputElement]) = Callback {
      resetFileSelector
      reader = new FileReader
      file = e.target.files.item(0)
      dom.console.log("before get size")
      try {
        fileSize = file.get.size
      }
      catch {
        case  e : UndefinedBehaviorError =>
          dom.console.log(s"Undefined:${e.getMessage}")
          val r = "An undefined behavior was detected: ([0-9]+) is not an instance of java.lang.Integer".r
          r.findFirstMatchIn(e.getMessage) match {
            case Some(m) => fileSize = BigInt(m.group(1) )
            case None =>
              dom.console.log(s"Error loading file ${file.get.name}")
              fileSize = 0
          }
        case default : Throwable =>
          dom.console.log(s"Throwable ${default}" )
      }

      $.modState( s =>
        s.copy( state = ChooseFields, progress = ProgressMeter( file ))
      ).runNow()

      dom.console.log(s"after get size ${fileSize}" )
      reader.onload = getHeaders
      reader.readAsText(file.get.slice(0, blockSize))
      dom.console.log("readHeaders finished")
    }

    def cancelProcessFile( e:  ReactEventI) : Callback = {
      reader.abort()
      resetFileSelector
      abort = true
      val props = $.props.runNow()
      //props.router.set(Home()).runNow()
      Callback.log  ("Cancel file processing")
    }

    def updateOnProgress = ( e: ProgressEvent ) => {
      Callback.log(s"${e.loaded} :: ${e.total} ").runNow()
    }

    def processFile(e:  ReactEventI): Callback = Callback {
      dom.console.log( "start to process file")
      $.modState( s =>
        s.copy(state = ProcessingFile)
      ).runNow()
      reader.onload = processChunk
      readFileLines
    }



    def readFileLines = {
      dom.console.log(s"reading file ${file.get.name} from : $start  to: $end")
      try {
        val slice = file.get.slice(start.toInt, end.toInt)
        reader.readAsText(slice)
        start = end
        end = start + blockSize
      }
      catch {
        case default : UndefinedBehaviorError =>
          dom.console.error( s"error on file Slice $start:$end ${default.getMessage} ")
      }
      dom.console.log(s"readFileLines ends ok!")
    }

    def toggleFieldVisibility( h : HeaderField, state: State)(e:  ReactEventI) = {
      val nstate = state.header.map{ s =>
        if ( s.pos == h.pos ) { HeaderField ( h.pos, h.imp, ! h.visible , h.value)}
        else { s }
      }
      $.modState(s => s.copy( header = nstate))
    }

    def toggleViewAll(e: ReactEventI) =  {
      val nstate = $.state.map( st=>
        st.header.map{ s =>
          HeaderField( s.pos, s.imp,! e.target.checked, s.value )
        }
      ).runNow()
      $.modState(s => s.copy( header = nstate))
    }

    def toggleImportAll(e: ReactEventI) =  {
      val nstate = $.state.map( st=>
        st.header.map{ s =>
          HeaderField( s.pos, ! e.target.checked, s.visible, s.value )
        }
      ).runNow()
      $.modState(s => s.copy(header = nstate))
    }

    def toggleFieldImp( h : HeaderField, state: State)(e:  ReactEventI) = {
      val nstate = state.header.map{ s =>
        if ( s.pos == h.pos ) { HeaderField ( h.pos, ! h.imp, h.visible , h.value)}
        else s
      }
      $.modState(s => s.copy(header = nstate))
    }

    def getStateOnChange ( state : State, field: Option[HeaderFieldCoor], e: ReactEventI ) : Tuple2[Option[HeaderFieldCoor], Seq[HeaderField]] = {
        field match {
          case Some(f) => //chr back to headers, remove selected from Headers
            state.header.filter(_.pos == e.target.value.toInt) match {
              case h if h.size != 0 =>
                Tuple2(
                  Some(HeaderFieldCoor(h(0).pos, h(0).imp, h(0).visible, h(0).value)),
                  (state.header.filter(_.pos != e.target.value.toInt) :+ HeaderField( f.pos, f.imp,f.visible, f.value) )
                    .sortWith((a,b) => a.pos < b.pos)
                )
              case default => (None, state.header)
            }
          case None => // remove from headers,
            state.header.filter(h => h.pos == e.target.value.toInt) match {
              case h if h.size != 0 =>
                Tuple2(
                  Some(HeaderFieldCoor(h(0).pos, h(0).imp, h(0).visible, h(0).value)),
                  state.header.filter(_.pos != e.target.value.toInt).sortWith((a,b) => a.pos < b.pos)
                )
              case Nil => (None, state.header)
            }
        }
    }

    def setChrField(e: ReactEventI) = {
      val state = $.state.runNow()
      val newState = getStateOnChange(state, state.chr ,e )
      $.modState( s => s.copy( chr = newState._1,  header = newState._2 ) )
    }

    def setStartField(e: ReactEventI) = {
      val state = $.state.runNow()
      val newState = getStateOnChange(state,state.start,e )
      $.modState( s => s.copy( start = newState._1,  header = newState._2 ) )
    }

    def setEndField(e: ReactEventI) = {
      val state = $.state.runNow()
      val newState = getStateOnChange(state, state.end,e )
      $.modState( s => s.copy( end = newState._1,  header = newState._2 ) )
    }

    def selectField[A]( headers : Seq[HeaderField], selected : Option[HeaderFieldCoor], action : (ReactEventI) => Callback )  = {
      selected match{
        case Some(x) =>
          <.select(^.className:="form-control col-md-3", ^.value := x.pos, ^.onChange ==> action)(
            <.option (^.value := x.pos)(x.value.toString),
            headers.map ( s => <.option(^.value := s.pos)(s.value.toString)),
            <.option (^.value := -1)("None")
          )
        case None =>
          <.select(^.className:="form-control", ^.value := -1, ^.onChange ==> action)(
            <.option (^.value := -1)("None"),
            headers.map ( s => <.option(^.value := s.pos)(s.value.toString))
          )
      }
    }

    def chooseFile : ReactTagOf[HTMLElement] = {
      <.div (^.id:="file_select")(
        <.label("Welcome! select file:"),
        <.input(^.`type`:="file", ^.id := "input", ^.onChange ==> readHeaders)
      )
    }


    /*
    //Filters
    def setArbitraryFilter(state: State)(e:  ReactEventI) = Callback {
      //to remove just for proof of cocept
      filters.add(  ArbitraryFilter("intron_variant", 3) )
      getVariants(state.vBrowser.uuid, 0,varPerPage,filters.toSeq)
    }

    def removeFilters(state: State)(e:  ReactEventI) = Callback {
      filters.clear()
      filters.add(NoFilter() )

      jQuery(".filterText").`val`("")
      getVariants(state.vBrowser.uuid, 0, varPerPage -1,filters.toSeq)
    }

    def setFilter(state: State)(e:  ReactEventI) = Callback {
      //to remove just for proof of cocept

      filters.add( FilterByChr("chr1") )
      getVariants(state.vBrowser.uuid, 0, varPerPage -1,filters.toSeq)
    }

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

    def setVariantFilter (f : VariantFilterObject)(e: ReactEventI) = Callback {
      val state = $.state.runNow()
      val newFilter = f.getFilter(e.target.value)()
      val nFilters = removeRedundantFilters( newFilter, filters.toSet )
      filters.clear()
      nFilters.foreach( f =>  filters.add(f))
      filters.++=( removeRedundantFilters( newFilter, filters.toSet ) )

      getVariants(state.vBrowser.uuid, 0, varPerPage - 1 ,filters.toSeq)
    }

    def setVariantFilter (f : VariantFilterObject, pos: Int)(e: ReactEventI) = Callback {
      val state = $.state.runNow()
      val newFilter = f.getFilter(e.target.value)(pos)
      dom.console.log( s"filters before Redundant: $filters")
      val nFilters = removeRedundantFilters( newFilter, filters.toSet )
      filters.clear()
      nFilters.foreach( f =>  filters.add(f))

      dom.console.log( s"filters after Redundant: $filters")

      getVariants(state.vBrowser.uuid, 0, varPerPage -1,filters.toSeq)
    }

    def showFilters ( state : State ) = {
      <.div("Filters:")(
        <.span(^.className :="fa fa-times-circle", ^.onClick ==> removeFilters(state))("remove"),
        <.span(^.className :="fa fa-filter", ^.onClick ==> setFilter(state))("chr1 filter"),
        <.span(^.className :="fa fa-filter", ^.onClick ==> setArbitraryFilter(state))("arbitrary text on field 4")
      )
    }

    //refactor this to tabs
    */


    def selectFields ( state : State) = {
      <.div ( chooseFile,
        <.span(^.className:="glyphicon glyphicon-eye-close")(
          "view fields :",
          <.input(^.`type`:="checkbox", ^.onChange ==> toggleViewAll )
        ),
        <.span(^.className:="glyphicon glyphicon-ban-circle")(
          "import fields :",
          <.input(^.`type`:="checkbox", ^.onChange ==> toggleImportAll )
        ),
        <.div(^.id:="file_select col-md-2")(
          <.label("Select Chromosome field:"), selectField(state.header, state.chr, setChrField),

          <.label("Select start field:"), selectField(state.header,state.start, setStartField),

          <.label("Select end field:"), selectField(state.header,state.end, setEndField)
        ),
        state.header.map { h =>
          <.div(^.className:="header_field col-md-1" ) (
            <.label(^.id:=s"field_${h.pos}")(
              h.value.toString,
              if ( h.visible == true) {
                <.span(^.className:="glyphicon glyphicon-eye-open", ^.onClick ==> toggleFieldVisibility(h,state))}
              else {
                <.span(^.className:="glyphicon glyphicon-eye-close", ^.onClick ==> toggleFieldVisibility(h,state))
              }
              ,
              if ( h.imp == true ) {
                <.span(^.className:="glyphicon glyphicon-ok", ^.onClick ==> toggleFieldImp(h,state)) }
              else {
                <.span(^.className:="glyphicon glyphicon-ban-circle", ^.onClick ==> toggleFieldImp(h,state))
              }
            )
          )},
        <.button( ^.className:="btn btn-primary col-md-push-3 col-md-offset-3", ^.`type`:= "submit", ^.onClick ==> processFile)("continue"),
        <.button( ^.className:="btn btn-warning col-md-push-3 col-md-offset-4", ^.`type`:= "submit", ^.onClick ==> cancelProcessFile)("abort")
      )
    }

    def showErrors( state : State) = {
      val toogle = ( state.errors.size == 0 ) match {
        case true => "none"
        case false => "block"
      }

      <.div ( ^.className:="col-md-4 panel panel-red", ^.display:=toogle )(
        <.div (^.className := "panel-heading")("Errors"),
        <.div ( ^.className := "panel-body")(
          state.errors.map( <.p( _))
        )
      )
    }

    def alertClass( i : Int) : String = {
      if ( i == 0) {
        "alert alert-success"
      }
      else {
        "alert alert-danger"
      }
    }

    def processingFile( state: State) : ReactTagOf[HTMLElement] = {
      val percent = ( BigDecimal( state.progress.bytesRead.toString ) / BigDecimal( state.progress.file.get.size.toString )) * 100.0

      <.div(^.className :=  alertClass(state.errors.size)) (
        f"Processing File ${state.progress.file.get.name} Loaded : $percent%.2f %% NumVariants:  ${state.progress.numVariant} Errors : ${state.errors.size}",
        <.span(^.className:="glyphicon glyphicon-cancel", ^.onClick ==> cancelProcessFile )
        //browsing(state)
      )

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


    def browsing( state : State) = {


      /* Move this beheivor to browsing tabs
      def pageIntervals( total : Int , start: Int) : Seq[Tuple2[Int,Int]] = {
        if( total <= varPerPage && start == 1) {
          Seq()
        }
        else {
          total <= varPerPage match {
            case true => Seq ( Tuple2( start, start + total) )
            case false => Seq( Tuple2( start, start + varPerPage -1 )) ++ pageIntervals( total - varPerPage, start + varPerPage)
          }
        }
      }

      val percent = ( BigDecimal( state.progress.bytesRead.toString ) / BigDecimal( state.progress.file.get.size.toString )) * 100.0
      val intervals = pageIntervals(state.vBrowser.numVariants, 1)

      val pageNav = <.nav(^.className:="panel panel-default navbar-fixed-bottom") (
        <.ul(^.className:="pagination")(
          for (i <- intervals) yield{
            i match {
              case x if  x == intervals.head => <.li( <.span(<.i( ^.className:="glyphicon glyphicon-circle-arrow-left", ^.onClick ==>  gotoNavegation(state.vBrowser.uuid, x._1, x._2))) )
              case x if x == intervals.last && intervals.size != 1 => <.li( <.span( <.i( ^.className:="glyphicon glyphicon-circle-arrow-right",  ^.onClick ==> gotoNavegation(state.vBrowser.uuid,x._1, x._2))) )
              case default => <.li( <.span(<.i(^.className:="glyphicon glyphicon-unchecked",  ^.onClick ==> gotoNavegation(state.vBrowser.uuid,default._1, default._2))) )
            }
          }
        )
      )

      val header = <.tr()( state.chr match {
            case Some(ch) => <.th()(
              ch.value
              ,
              ch.filtersAvailable.map { f =>
                <.div(^.className:="input-group")(
                  <.span(^.className:="input-group-addon")(f.name),
                  <.input(^.className := "form-control filterText",^.`type`:="text", ^.onChange ==> setVariantFilter(f) )
                )
              }

            )
            case None => <.th()("None-chr")
          },
          state.start match {
            case Some(s) => <.th()(
              s.value
            )
            case None => "None-Start"
          },
          state.end match {
            case Some(s) => <.th()(s.value)
            case None => "None-end"
          }, state.header.filter( h => h.imp && h.visible ).map { h =>
                <.th(^.className:="")(

                      <.div(^.className:="btn-group", "role".reactAttr := "group")(
                        <.span(^.className:="btn btn-default btn-xs") (h.value),
                        <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-eye-close",^.`type`:="button"), //( <.i(^.className:="glyphicon glyphicon-eye-close")),
                        <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-sort",^.`type`:="button"), //( <.i(^.className:="fa fa-sort")),
                        <.button(^.className:="btn btn-default btn-xs glyphicon glyphicon-filter",^.`type`:="button",
                          ^.id :=s"filter-dropdown-${h.pos}", ^.onClick ==> toggleHeaderFilters( s"filter-dropdown-${h.pos}") ),
                          //"data-toggle".reactAttr:="dropdown", "aria-expanded".reactAttr:="false", ^.id :=s"filter-dropdown-${h.pos}"),
                         // ( <.i(^.className:="fa fa-filter")),
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

      val rows = state.vBrowser.vList.s.map{ v =>
          <.tr(
            <.td(v.coord.chr), <.td(v.coord.start.toString()), <.td( v.coord.end.getOrElse(".").toString),
            state.header.filter(j => j.imp && j.visible).map { h =>
                v.fieldSet.isDefinedAt( h.pos - 3 ) match  {
                case true  =>  <.td(^.className:="col-md-3") (v.fieldSet(h.pos -3 ).value)
                case false =>  <.td(^.className:="col-md-3") (s"${h.pos - 3 } not defined ${v.fieldSet}" )
              }
            }
          )
      }

      <.div(^.className:="panel panel-default") (
        <.div(^.className:= "well well-sm")(
          <.p(f"File ${state.progress.file.get.name} $percent%.2f %% ${state.progress.file.get.name} : " +
          s"${state.progress.file.get.size.toString} / ${state.progress.bytesRead.toString } " +
          s"NumVariants: ${state.vBrowser.numVariants} NumVariantsFiltered: ${state.vBrowser.numFilteredVariants} errors : ${state.errors.size}"),
          showErrors(state),
          showFilters(state)
        ),
        <.div(^.className:="table-responsive")(
          pageNav,
          <.table(^.id:="entityList", ^.className:="table table-hover table-bordered") (
            <.thead()(header),
            <.tbody()(rows)
          )
        )
      )
  */
    }


    def render(props: Props, state : State) = {
      dom.console.log(s"VariantBrowserFileSelector Component render: ${state.state.toString}")
      state.state match {
        case SelectFile => chooseFile
        case ChooseFields => selectFields(state)
        case ProcessingFile => processingFile(state)
        case Browsing =>  processingFile(state) // <.div( FilterTab( "New Tab", state.chr.get , state.start.get, state.end,state.header ) )
          // browsing(state)

      }
    }
  }


  @JSName("FileUpload")
  val component = ReactComponentB[Props]("FileUpload")
    .initialState[State](State())  // ( SelectFile, ProgressMeter(), None, None, None,Seq(), Seq(), UUID.randomUUID() ) )
    .renderBackend[Backend]
    .componentDidMount( scope => scope.backend.mounted(scope.props) )
    .build

 // def apply( r: RouterCtl[Loc],  page :  Loc) : ReactElement = component( Props(r,page))
  def apply ( x : (ReactElement) => Callback ): ReactElement = component( Props(x) )

}
