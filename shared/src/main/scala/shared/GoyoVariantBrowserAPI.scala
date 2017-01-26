package shared

import java.util.UUID

import shared.variants.{VariantFilter, VariantList, Variant}

import scala.concurrent.Future


/**
  * Created by jcsilla on 16/03/16.
  */

trait GoyoVariantBrowserAPI {

  def persistVariants( uuid:  UUID, v : Seq[Variant]) : Int

  def getVariants( uuid: UUID, filters : Seq[VariantFilter], passPage : Tuple2[Int, Int], filterPage : Tuple2[Int,Int] ) : Future[VariantList]

}

