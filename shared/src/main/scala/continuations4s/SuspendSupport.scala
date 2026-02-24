package continuations4s

/** The delimited continuation suspension interface. Represents a suspended
  * computation asking for a value of type `T` to continue (and eventually
  * returning a value of type `R`).
  */
trait Suspension[-T, +R] {
  def resume(arg: T): R
}

/** Support for suspension capabilities through a delimited continuation
  * interface.
  */
trait SuspendSupport {
  /** A marker for the "limit" of "delimited continuation". */
  type Label[R]

  /** The provided suspension type. */
  type Suspension[-T, +R] <: continuations4s.Suspension[T, R]

  /** Set the suspension marker as the body's caller, and execute `body`. */
  def boundary[R](body: Label[R] ?=> R): R

  /** Should return immediately if resume is called from within body */
  def suspend[T, R](body: this.Suspension[T, R] => R)(using Label[R]): T
}

object SuspendSupport extends SuspendSupportForPlatform
