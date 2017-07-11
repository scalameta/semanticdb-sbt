scalacOptions := {
  Keys.scalacOptions.value ++ sys.props("sbthost.config").split("\007")
}
