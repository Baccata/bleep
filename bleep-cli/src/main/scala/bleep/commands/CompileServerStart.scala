package bleep
package commands

import bleep.bsp.{BleepRifleLogger, CompileServerMode, SetupBloopRifle}
import bleep.internal.{BspClientDisplayProgress, Lazy}
import bleep.logging.Logger

import java.nio.file.Files
import scala.build.bloop.{BloopServer, BloopThreads}
import scala.build.blooprifle.BloopRifle
import scala.concurrent.ExecutionContext

case class CompileServerStart(logger: Logger, userPaths: UserPaths, lazyResolver: Lazy[CoursierResolver]) extends BleepCommand {
  override def run(): Either[BuildException, Unit] =
    BleepConfig
      .rewritePersisted(logger, userPaths)(_.copy(compileServerMode = CompileServerMode.Shared))
      .flatMap { bleepConfig =>
        val threads = BloopThreads.create()
        val jvm = JvmCmd(logger, bleepConfig.compileServerJvm, ExecutionContext.fromExecutorService(threads.jsonrpc))
        val bleepRifleLogger = new BleepRifleLogger(logger)
        val rifleConfig = SetupBloopRifle(jvm, userPaths, lazyResolver, bleepConfig.compileServerMode, bleepRifleLogger)
        if (BloopRifle.check(rifleConfig, bleepRifleLogger)) {
          Right(logger.info("Compile server is already running"))
        } else {
          val tempDir = Files.createTempDirectory("bleep-bloop")
          BloopServer.buildServer(
            config = rifleConfig,
            clientName = "bleep",
            clientVersion = BleepVersion.version,
            workspace = tempDir,
            classesDir = tempDir / "classes",
            buildClient = BspClientDisplayProgress(logger),
            threads = threads,
            logger = bleepRifleLogger
          )
          Right(())
        }
      }
}
