package continuations4s

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** Support for suspension capabilities through a delimited continuation
  * interface.
  */
trait Continuations {
  /** A marker for the "limit" of "delimited continuation". */
  type Label[R]

  /** The provided suspension type. */
  type Suspension[-T, +R] <: SuspensionBase[T, R]

  /** Platform-specific result of [[boundary]]. */
  type Boundary[+R]

  /** Set the suspension marker as the body's caller, and execute `body`.
    *
    * @return
    *   the result of the boundary. Note that this is platform-specific. Ideally
    *   we could return just `R`, however, this either implies platform support
    *   for delimited continuations (Scala Native), or blocking threads (JVM).
    *   On top of JS/WASM engines, blocking can only happen in the context of a
    *   `js.async` scope, therefore, we have to work with `js.Promise`.
    */
  def boundary[R](body: Label[R] ?=> R): Boundary[R]

  /** Variant of [[boundary]] that returns the result as a
    * `scala.concurrent.Future`.
    *
    * Common-denominator between platforms, useful for testing.
    */
  def boundaryAsFuture[R](body: Label[R] ?=> R)(using ExecutionContext): Future[R]

  /** Should return immediately if resume is called from within body */
  def suspend[T, R](body: this.Suspension[T, R] => R)(using Label[R]): T

  /** The delimited continuation suspension interface. Represents a suspended
    * computation asking for a value of type `T` to continue (and eventually
    * returning a value of type `R`, which is the result that the whole
    * delimited continuation will eventually produce).
    */
  trait SuspensionBase[-T, +R] {
    /** Resume the suspended computation with `arg`.
      *
      * @param arg
      *   the value to resume the suspended computation with, which is the value
      *   that the suspended computation is asking for.
      *
      * @return
      *   the rest of the computation, i.e., the result that the whole delimited
      *   continuation will eventually produce, after resuming with `arg`
      */
    def resume(arg: T): Boundary[R]

    /** The return of [[resume]] being platform-specific, this variant is useful
      * for abstracting over platform-specific code, e.g., testing.
      *
      * It can also make [[resume]] safer, because it forces an asynchronous
      * boundary.
      */
    def resumeAsFuture(arg: T)(using ExecutionContext): Future[R]

    /** A variant of [[resume]] that doesn't wait for the result of the
      * boundary.
      */
    def resumeAndForget(arg: T): Unit
  }
}

object Continuations extends ContinuationsForPlatform
