package com.github.skennedy.fortify

import cats.effect.*
import cats.effect.std.{Console, Random}
import cats.implicits.*
import com.comcast.ip4s.*
import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp
import epollcat.EpollApp
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder, Json}
import org.http4s.*
import org.http4s.circe.jsonOf
import org.http4s.dsl.request.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.syntax.all.*

import java.net.InetAddress
import scala.concurrent.duration.*

object Main extends EpollApp {

  private val hostOpt = Opts
    .option[String]("host", help = "host for Automate to reach this computer on", short = "h")
    .map(IO.pure)
    .withDefault(IO(InetAddress.getLocalHost.getHostName))
  private val userOpt   = Opts.option[String]("user", help = "Automate user account", short = "u")
  private val secretOpt = Opts.option[String]("secret", help = "Automate user secret", short = "s")
  private val timeoutOpt =
    Opts.option[FiniteDuration]("timeout", help = "Timeout", short = "t").withDefault(60.seconds)

  def run(args: List[String]): IO[ExitCode] =
    CommandIOApp.run(command, args)

  private val command: Command[IO[ExitCode]] =
    Command(
      "fortify",
      "Trigger Automate pipeline to retrieve FortiToken OTP code"
    ) {
      (hostOpt, userOpt, secretOpt, timeoutOpt).mapN { (hostIO, user, secret, timeout) =>
        (hostIO, randomPort).mapN { (host, port) =>
          startServerToReceiveToken(port, timeout) { waitForToken =>
            triggerAutomateFlow(host, port, user, secret) >>
              waitForToken
                .flatMap(Console[IO].println)
                .as(ExitCode.Success)
          }
        }.flatten
      }
    }

  private def startServerToReceiveToken[A](port: Port, timeout: Duration)(
      f: IO[String] => IO[A]
  ): IO[A] =
    Deferred[IO, String]
      .flatMap { tokenDeferred =>
        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(port)
          .withHttp2
          .withHttpApp(receiveAutomateResponse(tokenDeferred).orNotFound)
          .build
          .use(_ => f(tokenDeferred.get.timeout(timeout)))
      }

  private def randomPort =
    Random
      .scalaUtilRandom[IO]
      .flatMap(_.nextIntBounded(Port.MaxValue))
      .flatMap(p => IO.fromOption(Port.fromInt(p))(new IllegalArgumentException))

  def triggerAutomateFlow(host: String, port: Port, user: String, secret: String): IO[Unit] = {

    val serverUri = Uri(
      scheme = Some(Uri.Scheme.http),
      authority = Some(Uri.Authority(host = Uri.RegName(host), port = Some(port.value))),
      path = Uri.Path.Root
    )
    import org.http4s.circe.CirceEntityEncoder.*
    EmberClientBuilder.default[IO].build.use { client =>
      client.expect[Unit] {
        Request[IO](method = Method.POST, uri = uri"https://llamalab.com/automate/cloud/message")
          .withEntity(
            AutomateRequest(
              secret = secret,
              to = user,
              device = None,
              payload = AutomatePayload(serverUri)
            )
          )
      }
    }
  }

  def receiveAutomateResponse(value: Deferred[IO, String]): HttpRoutes[IO] = {
    import org.http4s.circe.CirceEntityDecoder.*

    HttpRoutes.of[IO] { case req @ POST -> Root =>
      req.as[AutomateResponse].flatMap { resp =>
        value
          .complete(resp.token)
          .as(Response[IO](Status.Ok))
      }
    }
  }
}

final case class AutomatePayload(
    fortifyServerUri: Uri
)

object AutomatePayload {
  import org.http4s.circe.*
  given encoder: Encoder[AutomatePayload] = deriveEncoder
}

final case class AutomateRequest(
    secret: String,
    to: String,
    device: Option[String],
    payload: AutomatePayload
)

object AutomateRequest {
  given encoder: Encoder[AutomateRequest] = deriveEncoder
}

final case class AutomateResponse(token: String)

object AutomateResponse {
  implicit val decoder: Decoder[AutomateResponse] = deriveDecoder
}
