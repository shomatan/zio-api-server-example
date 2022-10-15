import sun.jvm.hotspot.HelloWorld.e
import zhttp.http._
import zhttp.service.Server
import zio._
import zio.ZIOAppDefault
import zio.json._

import scala.collection.mutable

object Main extends ZIOAppDefault {
  def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    Server
      .start(
        port = 8080,
        http = UserApp()
      )
      .provide(
        InMemoryUserRepo.layer
      )
}

case class User(name: String, age: Int)

object User {
  implicit val userEncoder: JsonEncoder[User] = DeriveJsonEncoder.gen[User]
  implicit val userDecoder: JsonDecoder[User] = DeriveJsonDecoder.gen[User]
}

object UserApp {
  def apply(): Http[UserRepo, Throwable, Request, Response] =
    Http.collectZIO[Request] {
      // POST /users -d '{"name": "John", "age": 35}'
      case req @ Method.POST -> !! / "users" =>
        for {
          user <- req.bodyAsString.map(_.fromJson[User])
          result <- user match {
            case Right(user) =>
              UserRepo.register(user).map(id => Response.text(id))
            case Left(error) =>
              ZIO
                .debug(s"Failed to parse the input: $error")
                .as(
                  Response
                    .text(
                      "Failed to parse the input"
                    )
                    .setStatus(Status.BadRequest)
                )
          }
        } yield result
      // GET /users/:id
      case Method.GET -> !! / "users" / id =>
        UserRepo
          .lookup(id)
          .map {
            case Some(user) =>
              Response.json(user.toJson)
            case None =>
              Response.status(Status.NotFound)
          }
      // GET /users
      case Method.GET -> !! / "users" =>
        UserRepo.users.map(response => Response.json(response.toJson))
    }
}

// --- Definition of User Repository

trait UserRepo {
  def register(user: User): Task[String]
  def lookup(id: String): Task[Option[User]]
  def users: Task[List[User]]
}

object UserRepo {
  def register(user: User): ZIO[UserRepo, Throwable, String] =
    ZIO.serviceWithZIO[UserRepo](_.register(user))

  def lookup(id: String): ZIO[UserRepo, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UserRepo](_.lookup(id))

  def users: ZIO[UserRepo, Throwable, List[User]] =
    ZIO.serviceWithZIO[UserRepo](_.users)
}

// --- Definition of User Repository implementation

case class InMemoryUserRepo(map: Ref[mutable.Map[String, User]])
    extends UserRepo {
  override def register(user: User): UIO[String] =
    for {
      id <- Random.nextUUID.map(_.toString)
      _ <- map.updateAndGet(_ addOne (id, user))
    } yield id

  override def lookup(id: String): UIO[Option[User]] =
    map.get.map(_.get(id))

  override def users: Task[List[User]] =
    map.get.map(_.values.toList)
}

object InMemoryUserRepo {
  def layer: ZLayer[Any, Nothing, InMemoryUserRepo] =
    ZLayer.fromZIO(
      Ref.make(mutable.Map.empty[String, User]).map(new InMemoryUserRepo(_))
    )
}
