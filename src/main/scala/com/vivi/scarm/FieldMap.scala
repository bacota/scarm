package com.vivi.scarm

import scala.language.implicitConversions

import scala.reflect.runtime.universe.{Type,TypeTag}

import shapeless.{ ::, HList, HNil, LabelledGeneric, Lazy, Witness }
import shapeless.labelled.FieldType



trait FieldMap[A] {
  //Maps field name to field type and nullability
  val mapping: Map[String,(Type, Boolean)]

  def prefixed[A](prefix: String) = FieldMap.prefixed[A](prefix, mapping)

  lazy val nullable = FieldMap.nullable[A](mapping)

  def ++[B<:HList](other: FieldMap[B]):FieldMap[A::B] = FieldMap.concat(this,other)
}

trait PrimitiveFieldMap {

  implicit def primitiveFieldMap[K <: Symbol, H, T <: HList]
    (implicit
      witness: Witness.Aux[K],
      tMap: FieldMap[T],
      typeTag: TypeTag[H]
    ): FieldMap[FieldType[K, H] :: T] =
    new FieldMap[FieldType[K, H] ::T] {
      override val mapping =
        tMap.mapping + (witness.value.name.toString -> (typeTag.tpe, false))
    }

}

trait OptionFieldMap extends PrimitiveFieldMap {
  implicit def optionFieldMap[K <: Symbol, H, T <: HList]
    (implicit
      witness: Witness.Aux[K],
      hMap: Lazy[FieldMap[H]],
      tMap: FieldMap[T]
    ): FieldMap[FieldType[K, Option[H]] :: T] = {
    hMap.value.nullable.prefixed(witness.value.name.toString) ++ tMap
  }
}


object FieldMap extends OptionFieldMap {

  def apply[T](implicit fieldMap: FieldMap[T]): FieldMap[T] = fieldMap

  implicit def make[A,ARepr<:HList](
    implicit gen: LabelledGeneric.Aux[A, ARepr],
    generator: FieldMap[ARepr]
  ): FieldMap[A] = new FieldMap[A] {
    override val mapping = generator.mapping
  }

  implicit def stringFieldMap[K <: Symbol, T <: HList]
    (implicit
      witness: Witness.Aux[K],
      tMap: FieldMap[T],
      typeTag: TypeTag[String]
    ): FieldMap[FieldType[K, String] :: T] =
    new FieldMap[FieldType[K, String] ::T] {
      override val mapping =
        tMap.mapping + (witness.value.name.toString -> (typeTag.tpe, false))
    }

  implicit val hnilMap: FieldMap[HNil] = new FieldMap[HNil] {
    override val mapping = Map()
  }

  implicit def hconsMap[K<:Symbol, H, T <: HList](implicit
    witness: Witness.Aux[K],
    hMap: Lazy[FieldMap[H]],
    tMap: FieldMap[T]
  ): FieldMap[FieldType[K, H] :: T] =
    hMap.value.prefixed(witness.value.name) ++ tMap


  private[scarm] def prefixed[A](
    prefix: String,
    origin: Map[String,(Type,Boolean)]
  ): FieldMap[A] =
    new FieldMap[A] {
      val mapping =
        if (prefix == "id") origin
        else origin.map(p => (prefix + "_" + p._1, p._2))
    }

  private[scarm] def nullable[A](origin: Map[String, (Type,Boolean)]): FieldMap[A] =
    new FieldMap[A] {
      val mapping = origin.mapValues(p => (p._1,true))
    }

  private[scarm] def concat[A,B<:HList](l: FieldMap[A], r: FieldMap[B]) =
    new FieldMap[A :: B] {
      val mapping = l.mapping ++ r.mapping
    }
}

