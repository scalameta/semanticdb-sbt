val foo = "bar"
// Expressions
name := "basic" + foo
// bar
organization := /* buz */ "me.vican.jorge" + foo

test := Def.task {
  ()
}.value

// Definitions
val p1 = project.in( /* foo */ file(foo))
