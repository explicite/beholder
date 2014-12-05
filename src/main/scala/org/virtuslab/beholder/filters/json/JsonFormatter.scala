package org.virtuslab.beholder.filters.json

import play.api.libs.json._
import org.virtuslab.beholder.filters.forms.FormFilterField
import org.virtuslab.beholder.filters.{ Order, FilterDefinition }
import akka.io.Tcp.Write
import play.api.data.Forms._
import play.api.data.validation.ValidationError
import play.api.libs.json.JsArray
import org.virtuslab.beholder.filters.FilterDefinition
import play.api.libs.json.JsObject
import play.api.data.validation.ValidationError
import org.virtuslab.beholder.filters.Order

/**
 * Author: Krzysztof Romanowski
 */
class JsonFormatter[Entity <: Product](filterFields: Seq[JsonFilterField[_, _]], columnsNames: Seq[String]) {
  def jsonDefinition: JsValue = {
    JsObject(
      columnsNames.zip(filterFields.map(_.fieldDefinition))
    )
  }

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  private implicit val orderingFormatter: Format[Order] = (
    (__ \ "column").format[String] and
    (__ \ "asc").format[Boolean]
  )(Order.apply, unlift(Order.unapply))

  private val filterDataFormatter: Format[Seq[Option[Any]]] = new Format[Seq[Option[Any]]] {
    override def writes(o: Seq[Option[Any]]): JsValue = JsObject(
      columnsNames.zip(filterFields).zip(o).flatMap {
        case ((name, filterFiled), value) =>
          value.map(v => name -> filterFiled.writeFilter(v))
      }
    )

    override def reads(json: JsValue): JsResult[Seq[Option[Any]]] = json match {
      case jsObject: JsObject =>
        jsObject.keys -- columnsNames.toSet match {
          case badFields if !badFields.isEmpty =>
            JsError((JsPath(Nil), ValidationError("No such fields in filter: " + badFields)))
          case _ =>
            val fieldResults = columnsNames.map(jsObject.value.get).zip(filterFields).map {
              case (Some(value), field) => field.readFilter(value).map(Option.apply)
              case (None, _) => JsSuccess(None)
            }
            val (successes, errors) = fieldResults.partition(_.isSuccess)
            if (errors.isEmpty) {
              JsSuccess(successes.map(_.get))
            } else {
              errors.map(_.asInstanceOf[JsError]).fold(JsError())(_ ++ _)
            }

        }
      case _ => JsError((JsPath(Nil), ValidationError("Filter definition must be an object!")))

    }
  }

  private val filterDefinitionFormat: Format[FilterDefinition] =
    ((__ \ "take").format[Option[Int]] and
      (__ \ "skip").format[Option[Int]] and
      (__ \ "ordering").format[Option[Order]] and
      (__ \ "data").format(filterDataFormatter))(FilterDefinition.apply, unlift(FilterDefinition.unapply))

  private def entity2Json(data: Entity): JsValue =
    JsObject(
      columnsNames.zip(filterFields).zip(data.productIterator.toIterable).map {
        case ((name, filterFiled), value) => name -> filterFiled.writeValue(value)
      }
    )

  final def filterDefinition(from: JsValue): Option[FilterDefinition] = from.asOpt(filterDefinitionFormat)

  final def results(from: FilterDefinition, data: Seq[Entity]): JsValue = JsObject(Seq(
    "filter" -> Json.toJson(from)(filterDefinitionFormat),
    "data" -> JsArray(data.map(entity2Json))
  ))

}
