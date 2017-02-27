package services

import javax.inject.Inject

import anorm.Column.{className, nonNull, timestamp}
import anorm.{Column, Macro, MetaDataItem, RowParser, SQL, TypeDoesNotMatch}
import models.{Application, Coordinates}
import play.api.db.DBApi
import play.api.libs.json._
import anorm.SqlParser.get
import anorm._
import anorm.JodaParameterMetaData._

@javax.inject.Singleton
class ApplicationService @Inject()(dbapi: DBApi) extends AnormJson with AnormCoordinate {
  private val db = dbapi.database("default")

  @inline private def className(that: Any): String =
    if (that == null) "<null>" else that.getClass.getName

  implicit val coordinatesParser: RowParser[Coordinates] = Macro.namedParser[Coordinates]
  implicit val fieldsMapParser: anorm.Column[Map[String,String]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[Map[String,String]])
        case json: String =>
          Right(Json.parse(json).as[Map[String,String]])
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Map[String,String] for column $qualified"))
      }
    }
  implicit val fieldsListParser: anorm.Column[List[String]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[List[String]])
        case json: String =>
          Right(Json.parse(json).as[List[String]])
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Map[String,String] for column $qualified"))
      }
    }


  val simple: RowParser[Application] = Macro.parser[Application](
    "id", "city", "status", "name", "email", "type", "address", "creation_date", "coordinates", "phone", "fields", "files"
  )

  def findByApplicationId(applicationId: String) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE id = {id}").on('id -> applicationId).as(simple.singleOpt)
  }

  def insert(application: Application) = db.withConnection { implicit connection =>
    SQL(
      """
          INSERT INTO application VALUES (
            {id}, {city}, {status}, {name}, {email}, {type}, {address}, {creation_date}, point({latitude}, {longitude}), {phone}, {fields}, {files}
          )
      """
    ).on(
      'id -> application.id,
      'city -> application.city,
      'status -> application.status,
      'name -> application.name,
      'email -> application.email,
      'type -> application._type,
      'address -> application.address,
      'creation_date -> application.creationDate,
      'latitude -> application.coordinates.latitude,
      'longitude -> application.coordinates.longitude,
      'phone -> application.phone,
      'fields -> Json.toJson(application.fields),
      'files -> Json.toJson(application.files)
    ).executeUpdate()
  }
  def findByCity(city: String) = db.withConnection { implicit connection =>
    SQL("SELECT * FROM application WHERE city = {city} ORDER BY creation_date DESC").on('city -> city).as(simple.*)
  }

  def updateStatus(id: String, newStatus: String) = db.withConnection { implicit connection =>
    SQL("UPDATE application SET status = {status} WHERE id = {id}"
    ).on(
      'id -> id,
      'status -> newStatus
    ).executeUpdate()
  }
}