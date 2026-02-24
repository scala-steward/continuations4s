package continuations4s
package native

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.scalanative.runtime.Continuations as nativeContinuations

private[continuations4s] trait NativeSuspend extends Continuations {
  override opaque type Label[R] = nativeContinuations.BoundaryLabel[R]
  override opaque type Suspension[-T, +R] <: SuspensionBase[T, R] = NativeContinuation[T, R]
  override type Boundary[+R] = R

  override def boundary[R](body: Label[R] ?=> R): R =
    nativeContinuations.boundary(body)

  override def boundaryAsFuture[R](body: Label[R] ?=> R)(using ExecutionContext): Future[R] =
    Future(blocking(boundary(body)))

  override def suspend[T, R](body: Suspension[T, R] => R)(using Label[R]): T =
    nativeContinuations.suspend[T, R](f => body(NativeContinuation(f)))

  final private[NativeSuspend] class NativeContinuation[-T, +R] private[continuations4s] (
    val cont: T => R
  ) extends SuspensionBase[T, R] {

    override def resume(arg: T): R =
      cont(arg)

    override def resumeAsFuture(arg: T)(using ExecutionContext): Future[R] =
      Future(blocking(resume(arg)))

    override def resumeAndForget(arg: T): Unit = {
      val _ = resume(arg)
    }
  }
}
