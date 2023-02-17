package bleep

import bleep.logging.Logger

import scala.concurrent.ExecutionContext

/** At this point we assert that we *have* a build. it's not necessarily loaded yet
  */
case class Prebootstrapped(
    logger: Logger,
    userPaths: UserPaths,
    buildPaths: BuildPaths,
    existingBuild: BuildLoader.Existing,
    ec: ExecutionContext
) {
  val cacheLogger = new BleepCacheLogger(logger)
  val fetchJvm = new FetchJvm(Some(userPaths.resolveJvmCacheDir), cacheLogger, ec)
  val fetchNode = new FetchNode(cacheLogger, ec)

  val resolvedJvm: Lazy[ResolvedJvm] =
    existingBuild.buildFile.map { maybeBuild =>
      val jvm = maybeBuild.orThrow.jvm.getOrElse {
        logger.warn(
          s"You build uses the default system JVM, which can change outside the build. for stable builds over time, let bleep manage your chosen JVM by adding it to ${BuildLoader.BuildFileName}"
        )
        model.Jvm.system
      }
      fetchJvm(jvm)
    }

  /** Will only reload if there are changes in the json structure, as indicated in the `Option`
    */
  def reloadFromDisk(): Either[BleepException, Option[Prebootstrapped]] =
    for {
      oldJson <- existingBuild.json.forceGet
      reloaded <- existingBuild.reloadFromDisk().existing
      newJson <- reloaded.json.forceGet
    } yield if (oldJson == newJson) None else Some(copy(existingBuild = reloaded))
}
