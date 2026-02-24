package continuations4s
package native

import scala.scalanative.runtime.{Continuations => nativeContinuations}

trait NativeSuspend extends SuspendSupport {
  type Label[R] = nativeContinuations.BoundaryLabel[R]
  type Suspension[T, R] = NativeContinuation[T, R]

  override def boundary[R](body: Label[R] ?=> R): R =
    nativeContinuations.boundary(body)

  override def suspend[T, R](body: Suspension[T, R] => R)(using Label[R]): T =
    nativeContinuations.suspend[T, R](f => body(NativeContinuation(f)))
} // end NativeSuspend

final class NativeContinuation[-T, +R] private[continuations4s] (val cont: T => R)
  extends Suspension[T, R] {

  def resume(arg: T): R =
    cont(arg)
}
