/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package play.api.http

import java.nio.charset.StandardCharsets

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import com.typesafe.config.ConfigMemorySize
import io.jsonwebtoken.SignatureAlgorithm
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import play.api._
import play.api.libs.Codecs
import play.api.mvc.Cookie.SameSite
import play.core.cookie.encoding.ClientCookieDecoder
import play.core.cookie.encoding.ClientCookieEncoder
import play.core.cookie.encoding.ServerCookieDecoder
import play.core.cookie.encoding.ServerCookieEncoder

/**
 * HTTP related configuration of a Play application
 *
 * @param context       The HTTP context
 * @param parser        The parser configuration
 * @param session       The session configuration
 * @param flash         The flash configuration
 * @param fileMimeTypes The fileMimeTypes configuration
 */
case class HttpConfiguration(
    context: String = "/",
    parser: ParserConfiguration = ParserConfiguration(),
    actionComposition: ActionCompositionConfiguration = ActionCompositionConfiguration(),
    cookies: CookiesConfiguration = CookiesConfiguration(),
    session: SessionConfiguration = SessionConfiguration(),
    flash: FlashConfiguration = FlashConfiguration(),
    fileMimeTypes: FileMimeTypesConfiguration = FileMimeTypesConfiguration(),
    secret: SecretConfiguration = SecretConfiguration()
)

/**
 * The application secret. Must be set. A value of "changeme" will cause the application to fail to start in
 * production.
 *
 * With the Play secret we want to:
 *
 * 1. Encourage the practice of *not* using the same secret in dev and prod.
 * 2. Make it obvious that the secret should be changed.
 * 3. Ensure that in dev mode, the secret stays stable across restarts.
 * 4. Ensure that in dev mode, sessions do not interfere with other applications that may be or have been running
 *   on localhost.  Eg, if I start Play app 1, and it stores a PLAY_SESSION cookie for localhost:9000, then I stop
 *   it, and start Play app 2, when it reads the PLAY_SESSION cookie for localhost:9000, it should not see the
 *   session set by Play app 1.  This can be achieved by using different secrets for the two, since if they are
 *   different, they will simply ignore the session cookie set by the other.
 *
 * To achieve 1 and 2, we will, in Activator templates, set the default secret to be "changeme".  This should make
 * it obvious that the secret needs to be changed and discourage using the same secret in dev and prod.
 *
 * For safety, if the secret is not set, or if it's changeme, and we are in prod mode, then we will fail fatally.
 * This will further enforce both 1 and 2.
 *
 * To achieve 3, if in dev or test mode, if the secret is either changeme or not set, we will generate a secret
 * based on the location of application.conf.  This should be stable across restarts for a given application.
 *
 * To achieve 4, using the location of application.conf to generate the secret should ensure this.
 *
 * Play secret is checked for a minimum length, dependent on the algorithm used to sign the session and flash cookie.
 * If the key has fewer bits then required by the algorithm, then an error is thrown and the configuration is invalid.
 *
 * @param secret   the application secret
 * @param provider the JCE provider to use. If null, uses the platform default
 */
case class SecretConfiguration(secret: String = "changeme", provider: Option[String] = None)

/**
 * The cookies configuration
 *
 * @param strict Whether strict cookie parsing should be used. If true, will cause the entire cookie header to be
 *               discarded if a single cookie is found to be invalid.
 */
case class CookiesConfiguration(strict: Boolean = true) {
  val serverEncoder: ServerCookieEncoder = if (strict) ServerCookieEncoder.STRICT else ServerCookieEncoder.LAX
  val serverDecoder: ServerCookieDecoder = if (strict) ServerCookieDecoder.STRICT else ServerCookieDecoder.LAX
  val clientEncoder: ClientCookieEncoder = if (strict) ClientCookieEncoder.STRICT else ClientCookieEncoder.LAX
  val clientDecoder: ClientCookieDecoder = if (strict) ClientCookieDecoder.STRICT else ClientCookieDecoder.LAX
}

/**
 * The session configuration
 *
 * @param cookieName The name of the cookie used to store the session
 * @param secure     Whether the session cookie should set the secure flag or not
 * @param maxAge     The max age of the session, none, use "session" sessions
 * @param httpOnly   Whether the HTTP only attribute of the cookie should be set
 * @param domain     The domain to set for the session cookie, if defined
 * @param path       The path for which this cookie is valid
 * @param sameSite   The cookie's SameSite attribute
 * @param partitioned Whether the Partitioned attribute of the cookie should be set
 * @param jwt        The JWT specific information
 */
case class SessionConfiguration(
    cookieName: String = "PLAY_SESSION",
    secure: Boolean = false,
    maxAge: Option[FiniteDuration] = None,
    httpOnly: Boolean = true,
    domain: Option[String] = None,
    path: String = "/",
    sameSite: Option[SameSite] = Some(SameSite.Lax),
    partitioned: Boolean = false,
    jwt: JWTConfiguration = JWTConfiguration()
)

/**
 * The flash configuration
 *
 * @param cookieName The name of the cookie used to store the session
 * @param secure     Whether the flash cookie should set the secure flag or not
 * @param httpOnly   Whether the HTTP only attribute of the cookie should be set
 * @param domain     The domain to set for the session cookie, if defined
 * @param path       The path for which this cookie is valid
 * @param sameSite   The cookie's SameSite attribute
 * @param partitioned Whether the Partitioned attribute of the cookie should be set
 * @param jwt        The JWT specific information
 */
case class FlashConfiguration(
    cookieName: String = "PLAY_FLASH",
    secure: Boolean = false,
    httpOnly: Boolean = true,
    domain: Option[String] = None,
    path: String = "/",
    sameSite: Option[SameSite] = Some(SameSite.Lax),
    partitioned: Boolean = false,
    jwt: JWTConfiguration = JWTConfiguration()
)

/**
 * Configuration for body parsers.
 *
 * @param maxMemoryBuffer The maximum size that a request body that should be buffered in memory.
 * @param maxDiskBuffer   The maximum size that a request body should be buffered on disk.
 * @param allowEmptyFiles If empty file uploads are allowed (no matter if filename or file is empty)
 */
case class ParserConfiguration(
    maxMemoryBuffer: Long = 102400,
    maxDiskBuffer: Long = 10485760,
    allowEmptyFiles: Boolean = false
)

/**
 * Configuration for action composition.
 *
 * @param controllerAnnotationsFirst      If annotations put on controllers should be executed before the ones put on actions.
 * @param executeActionCreatorActionFirst If the action returned by the action creator should be
 *                                        executed before the action composition ones.
 * @param includeWebSocketActions         If WebSocket actions should be included in action composition.
 */
case class ActionCompositionConfiguration(
    controllerAnnotationsFirst: Boolean = false,
    executeActionCreatorActionFirst: Boolean = false,
    includeWebSocketActions: Boolean = false,
)

/**
 * Configuration for file MIME types, mapping from extension to content type.
 *
 * @param mimeTypes     the extension to mime type mapping.
 */
case class FileMimeTypesConfiguration(mimeTypes: Map[String, String] = Map.empty)

object HttpConfiguration {
  private val logger = LoggerFactory.getLogger(classOf[HttpConfiguration])

  def parseSameSite(config: Configuration, key: String): Option[SameSite] = {
    config.get[Option[String]](key).flatMap { value =>
      val result = SameSite.parse(value)
      if (result.isEmpty) {
        val values = SameSite.values.mkString(", ")
        logger.warn(s"""Assuming $key = null, since "$value" is not a valid SameSite value ($values)""")
      }
      result
    }
  }

  def parseFileMimeTypes(config: Configuration): Map[String, String] =
    config
      .get[String]("play.http.fileMimeTypes")
      .split('\n')
      .iterator
      .flatMap { l =>
        val line = l.trim

        line.splitAt(1) match {
          case ("", "") => Option.empty[(String, String)] // blank
          case ("#", _) => Option.empty[(String, String)] // comment

          case _ => // "foo=bar".span(_ != '=') -> (foo,=bar)
            line.span(_ != '=') match {
              case (key, v) => Some(key -> v.drop(1))         // '=' prefix
              case _        => Option.empty[(String, String)] // skip invalid
            }
        }
      }
      .toMap

  def fromConfiguration(config: Configuration, environment: Environment) = {
    def getPath(key: String, deprecatedKey: Option[String] = None): String = {
      val path = deprecatedKey match {
        case Some(depKey) => config.getDeprecated[String](key, depKey)
        case None         => config.get[String](key)
      }
      if (!path.startsWith("/")) {
        throw config.globalError(s"$key must start with a /")
      }
      path
    }

    val context     = getPath("play.http.context", Some("application.context"))
    val sessionPath = getPath("play.http.session.path")
    val flashPath   = getPath("play.http.flash.path")

    if (config.has("mimetype")) {
      throw config.globalError("mimetype replaced by play.http.fileMimeTypes map")
    }

    val secretConfiguration = getSecretConfiguration(config, environment)
    HttpConfiguration(
      context = context,
      parser = ParserConfiguration(
        maxMemoryBuffer =
          config.getDeprecated[ConfigMemorySize]("play.http.parser.maxMemoryBuffer", "parsers.text.maxLength").toBytes,
        maxDiskBuffer = config.get[ConfigMemorySize]("play.http.parser.maxDiskBuffer").toBytes,
        allowEmptyFiles = config.get[Boolean]("play.http.parser.allowEmptyFiles")
      ),
      actionComposition = ActionCompositionConfiguration(
        controllerAnnotationsFirst = config.get[Boolean]("play.http.actionComposition.controllerAnnotationsFirst"),
        executeActionCreatorActionFirst =
          config.get[Boolean]("play.http.actionComposition.executeActionCreatorActionFirst"),
        includeWebSocketActions = config.get[Boolean]("play.http.actionComposition.includeWebSocketActions"),
      ),
      cookies = CookiesConfiguration(
        strict = config.get[Boolean]("play.http.cookies.strict")
      ),
      session = SessionConfiguration(
        cookieName = config.getDeprecated[String]("play.http.session.cookieName", "session.cookieName"),
        secure = config.getDeprecated[Boolean]("play.http.session.secure", "session.secure"),
        maxAge = config.getDeprecated[Option[FiniteDuration]]("play.http.session.maxAge", "session.maxAge"),
        httpOnly = config.getDeprecated[Boolean]("play.http.session.httpOnly", "session.httpOnly"),
        domain = config.getDeprecated[Option[String]]("play.http.session.domain", "session.domain"),
        sameSite = parseSameSite(config, "play.http.session.sameSite"),
        path = sessionPath,
        partitioned = config.getDeprecated[Boolean]("play.http.session.partitioned", "session.partitioned"),
        jwt = JWTConfigurationParser(config, secretConfiguration, "play.http.session.jwt")
      ),
      flash = FlashConfiguration(
        cookieName = config.getDeprecated[String]("play.http.flash.cookieName", "flash.cookieName"),
        secure = config.get[Boolean]("play.http.flash.secure"),
        httpOnly = config.get[Boolean]("play.http.flash.httpOnly"),
        domain = config.get[Option[String]]("play.http.flash.domain"),
        sameSite = parseSameSite(config, "play.http.flash.sameSite"),
        path = flashPath,
        partitioned = config.get[Boolean]("play.http.flash.partitioned"),
        jwt = JWTConfigurationParser(config, secretConfiguration, "play.http.flash.jwt")
      ),
      fileMimeTypes = FileMimeTypesConfiguration(
        parseFileMimeTypes(config)
      ),
      secret = secretConfiguration
    )
  }

  private def getSecretConfiguration(config: Configuration, environment: Environment): SecretConfiguration = {
    val Blank = """\s*""".r

    val secret =
      config.get[Option[String]]("play.http.secret.key") match {
        case (Some("changeme") | Some(Blank()) | None) if environment.mode == Mode.Prod =>
          val message =
            """
              |The application secret has not been set, and we are in prod mode. Your application is not secure.
              |To set the application secret, please read https://playframework.com/documentation/latest/ApplicationSecret
              |""".stripMargin
          throw config.reportError("play.http.secret", message)

        case Some("changeme") | Some(Blank()) | None =>
          val appConfLocation = environment.resource("application.conf")
          // Try to generate a stable secret. Security is not the issue here, since this is just for tests and dev mode.
          val secret = appConfLocation.fold(
            // No application.conf?  Oh well, just use something hard coded.
            "she sells sea shells on the sea shore"
          )(_.toString)
          // We want 64 bytes / 512 bits to support HS512 so we append a second md5
          val md5Secret = Codecs.md5(secret) + Codecs.md5("the shells she sells are sea-shells")
          logger.debug(
            s"Generated dev mode secret $md5Secret for app at ${appConfLocation.getOrElse("unknown location")}"
          )
          md5Secret
        case Some(s) => s
      }

    val provider = config.getDeprecated[Option[String]]("play.http.secret.provider", "play.crypto.provider")

    SecretConfiguration(String.valueOf(secret), provider)
  }

  /**
   * For calling from Java.
   */
  def createWithDefaults() = apply()

  @Singleton
  class HttpConfigurationProvider @Inject() (configuration: Configuration, environment: Environment)
      extends Provider[HttpConfiguration] {
    lazy val get = fromConfiguration(configuration, environment)
  }

  @Singleton
  class ParserConfigurationProvider @Inject() (conf: HttpConfiguration) extends Provider[ParserConfiguration] {
    lazy val get = conf.parser
  }

  @Singleton
  class CookiesConfigurationProvider @Inject() (conf: HttpConfiguration) extends Provider[CookiesConfiguration] {
    lazy val get = conf.cookies
  }

  @Singleton
  class SessionConfigurationProvider @Inject() (conf: HttpConfiguration) extends Provider[SessionConfiguration] {
    lazy val get = conf.session
  }

  @Singleton
  class FlashConfigurationProvider @Inject() (conf: HttpConfiguration) extends Provider[FlashConfiguration] {
    lazy val get = conf.flash
  }

  @Singleton
  class ActionCompositionConfigurationProvider @Inject() (conf: HttpConfiguration)
      extends Provider[ActionCompositionConfiguration] {
    lazy val get = conf.actionComposition
  }

  @Singleton
  class FileMimeTypesConfigurationProvider @Inject() (conf: HttpConfiguration)
      extends Provider[FileMimeTypesConfiguration] {
    lazy val get = conf.fileMimeTypes
  }

  @Singleton
  class SecretConfigurationProvider @Inject() (conf: HttpConfiguration) extends Provider[SecretConfiguration] {
    lazy val get: SecretConfiguration = conf.secret
  }
}

/**
 * The JSON Web Token configuration
 *
 * @param signatureAlgorithm The signature algorithm used to sign the JWT
 * @param expiresAfter The period of time after which the JWT expires, if any.
 * @param clockSkew The amount of clock skew to permit for expiration / not before checks
 * @param dataClaim The claim key corresponding to the data map passed in by the user
 */
case class JWTConfiguration(
    signatureAlgorithm: String = "HS256",
    expiresAfter: Option[FiniteDuration] = None,
    clockSkew: FiniteDuration = 30.seconds,
    dataClaim: String = "data"
)

object JWTConfigurationParser {
  def apply(
      config: Configuration,
      secretConfiguration: SecretConfiguration,
      parent: String
  ): JWTConfiguration = {
    JWTConfiguration(
      signatureAlgorithm = getSignatureAlgorithm(config, secretConfiguration, parent),
      expiresAfter = config.get[Option[FiniteDuration]](s"${parent}.expiresAfter"),
      clockSkew = config.get[FiniteDuration](s"${parent}.clockSkew"),
      dataClaim = config.get[String](s"${parent}.dataClaim")
    )
  }

  private def getSignatureAlgorithm(
      config: Configuration,
      secretConfiguration: SecretConfiguration,
      parent: String
  ): String = {
    val signatureAlgorithmPath      = s"${parent}.signatureAlgorithm"
    val signatureAlgorithm          = config.get[String](signatureAlgorithmPath)
    val minKeyLengthBits            = SignatureAlgorithm.forName(signatureAlgorithm).getMinKeyLength
    val applicationSecretLengthBits = secretConfiguration.secret.getBytes(StandardCharsets.UTF_8).length * 8
    if (applicationSecretLengthBits < minKeyLengthBits) {
      val message =
        s"""
           |The application secret is too short and does not have the recommended amount of entropy for algorithm $signatureAlgorithm defined at $signatureAlgorithmPath.
           |Current application secret bits: $applicationSecretLengthBits, minimal required bits for algorithm $signatureAlgorithm: $minKeyLengthBits.
           |To set the application secret, please read https://playframework.com/documentation/latest/ApplicationSecret
           |""".stripMargin
      throw config.reportError("play.http.secret.key", message)
    }
    signatureAlgorithm
  }
}
