import sbt._

object PackagingTypePlugin extends AutoPlugin {
  override val buildSettings: Nil.type = {
    sys.props += "packaging.type" -> "jar"
    Nil
  }
}
