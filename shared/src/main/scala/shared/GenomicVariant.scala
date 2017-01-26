package shared.variants

import org.scalajs.dom

import scala.concurrent.Future

import boopickle.Default._

//import scala.collection.immutable.StreamViewLike.Filtered

/**
  * Created by jcsilla on 16/06/16.
  */

sealed trait VariantHeaderField

sealed case class HeaderFieldCoor(pos : Int, imp: Boolean = true, visible: Boolean = true , val value : String ) extends VariantHeaderField {
  val filtersAvailable = Seq[VariantFilterObject](FilterByChr)
}

sealed case class HeaderField( pos : Int, imp: Boolean = true, visible: Boolean = true , val value : String ) extends VariantHeaderField {
  val filtersAvailable = Seq[VariantFilterObject](ArbitraryFilter,NumericalLowerEqualFilter)
}

case class SingleVariantField( value : String) // extends VariantField

case class Coord ( chr: String, start: Int, end: Option[Int])

//sealed case class FieldSet( f : Seq[VariantField])
case class Variant( coord : Coord, fieldSet: Seq[SingleVariantField])

case class VariantSeq ( vars : Seq[Variant])

sealed case class VariantList ( vPass : Seq[Variant], numVPass : Int, vFilter : Seq[Variant],  numVFilter: Int )


//Filters for Variant
sealed trait VariantFilter {
  def applyFilter( v: Variant) : Boolean
}

trait VariantFilterObject {
  val name: String
  def getFilter(v: String)(i: Int = 0): VariantFilter
}


sealed case class FilterByChr( value : String) extends VariantFilter {
  def applyFilter( v: Variant ) = {
    v.coord.chr.toLowerCase.contains( value.toLowerCase )
  }
}

object FilterByChr extends VariantFilterObject {
  val name = "Chr"
  def getFilter(value: String)( i: Int ) : VariantFilter =  FilterByChr(value)
}



//TODO: FilterByStart, FilterByStart


sealed case class ArbitraryFilter(value: String, fieldSetOrder: Int) extends VariantFilter {
  def applyFilter( v: Variant) = {
    v.fieldSet.isDefinedAt( fieldSetOrder ) &&  v.fieldSet(fieldSetOrder).value.toLowerCase.contains(value.toLowerCase)
  }
}

object ArbitraryFilter extends VariantFilterObject {
  val name = "⊂"
  def getFilter( value: String)( fieldSetOrder : Int) : VariantFilter = ArbitraryFilter( value, fieldSetOrder)
}

sealed case class NumericalLowerEqualFilter( value : String, fieldSetOrder : Int) extends VariantFilter {
 def applyFilter( v : Variant) = {
   try {
     val nValue = value.toFloat
     v.fieldSet.isDefinedAt(fieldSetOrder) && v.fieldSet(fieldSetOrder).value.toFloat < nValue
   }
   catch  {
     case e : NumberFormatException => true
   }
 }
}

object NumericalLowerEqualFilter extends VariantFilterObject {
  val name = "≤"
  def getFilter ( value: String)( fieldSetOrder : Int) : VariantFilter = NumericalLowerEqualFilter( value, fieldSetOrder)
}

sealed case class NoFilter() extends  VariantFilter {
  def name = "No filter"
  def applyFilter( v: Variant ) =  true
}

