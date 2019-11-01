package web.utils

import cats.Show
import cats.effect.Sync
import cats.implicits._
import org.log4s

trait Logging {
  private implicit val log: log4s.Logger = log4s.getLogger(getClass)
  def getLogger[F[_]: Sync]: Logger[F]   = Logger.instance[F]
}

trait Logger[F[_]] {
  def error(msg: String): F[Unit]
  def error(msg: String, err: Throwable): F[Unit]
  def warn(msg: String): F[Unit]
  def warn(msg: String, err: Throwable): F[Unit]
  def info(msg: String): F[Unit]
  def debug(msg: String): F[Unit]
  def debug(msg: String, err: Throwable): F[Unit]
  def trace(msg: String): F[Unit]
  def trace(msg: String, err: Throwable): F[Unit]

  def error[A: Show](msg: A): F[Unit]
  def error[A: Show](msg: A, err: Throwable): F[Unit]
  def warn[A: Show](msg: A): F[Unit]
  def warn[A: Show](msg: A, err: Throwable): F[Unit]
  def info[A: Show](msg: A): F[Unit]
  def debug[A: Show](msg: A): F[Unit]
  def debug[A: Show](msg: A, err: Throwable): F[Unit]
  def trace[A: Show](msg: A): F[Unit]
  def trace[A: Show](msg: A, err: Throwable): F[Unit]
}

object Logger {
  def apply[F[_]](implicit L: Logger[F]): Logger[F] = L

  implicit def instance[F[_]](implicit F: Sync[F], logger: log4s.Logger): Logger[F] = new Logger[F] {
    def error(msg: String): F[Unit]                 = F.delay(logger.error(msg))
    def error(msg: String, err: Throwable): F[Unit] = F.delay(logger.error(err)(msg))
    def warn(msg: String): F[Unit]                  = F.delay(logger.warn(msg))
    def warn(msg: String, err: Throwable): F[Unit]  = F.delay(logger.warn(err)(msg))
    def info(msg: String): F[Unit]                  = F.delay(logger.info(msg))
    def debug(msg: String): F[Unit]                 = F.delay(logger.debug(msg))
    def debug(msg: String, err: Throwable): F[Unit] = F.delay(logger.debug(err)(msg))
    def trace(msg: String): F[Unit]                 = F.delay(logger.trace(msg))
    def trace(msg: String, err: Throwable): F[Unit] = F.delay(logger.trace(err)(msg))

    def error[A: Show](msg: A): F[Unit]                 = F.delay(logger.error(msg.show))
    def error[A: Show](msg: A, err: Throwable): F[Unit] = F.delay(logger.error(err)(msg.show))
    def warn[A: Show](msg: A): F[Unit]                  = F.delay(logger.warn(msg.show))
    def warn[A: Show](msg: A, err: Throwable): F[Unit]  = F.delay(logger.warn(err)(msg.show))
    def info[A: Show](msg: A): F[Unit]                  = F.delay(logger.info(msg.show))
    def debug[A: Show](msg: A): F[Unit]                 = F.delay(logger.debug(msg.show))
    def debug[A: Show](msg: A, err: Throwable): F[Unit] = F.delay(logger.debug(err)(msg.show))
    def trace[A: Show](msg: A): F[Unit]                 = F.delay(logger.trace(msg.show))
    def trace[A: Show](msg: A, err: Throwable): F[Unit] = F.delay(logger.trace(err)(msg.show))
  }
}
