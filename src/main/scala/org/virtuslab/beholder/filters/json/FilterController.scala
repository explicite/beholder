package org.virtuslab.beholder.filters.json

import org.virtuslab.beholder.filters.{ FilterDefinition, FilterAPI }
import play.api.mvc._
import org.virtuslab.unicorn.LongUnicornPlay.driver.simple.Session
import play.api.libs.json.{ JsString, JsValue }

/**
 * Author: Krzysztof Romanowski
 */
abstract class FilterController[Entity <: Product](filter: FilterAPI[Entity, JsonFormatter[Entity]]) extends Controller {

  def inSession(body: Request[AnyContent] => Session => Option[JsValue]): EssentialAction

  final def filterDefinition = inSession { request =>
    _ =>
      Option(filter.formatter.jsonDefinition)
  }

  //for filter modification such us setting default parameters etc.
  protected def mapFilterData(data: FilterDefinition) = data

  final def doFilter: EssentialAction =
    inSession {
      request =>
        implicit session =>
          for {
            json <- request.body.asJson
            filterDefinition <- filter.formatter.filterDefinition(json)
            data = filter.filter(mapFilterData(filterDefinition))
            totalResults = filter.totalResults(mapFilterData(filterDefinition))
          } yield filter.formatter.results(filterDefinition, data, totalResults)
    }
}
