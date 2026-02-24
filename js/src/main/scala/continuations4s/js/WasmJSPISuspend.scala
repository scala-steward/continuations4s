package continuations4s
package js

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.wasm.JSPI

/** An opaque, compile-time token to signal that we are under a [[js.async]]
  * scope.
  */
private[continuations4s] opaque type AsyncToken = Unit

private[continuations4s] object AsyncToken {
  /** Assumes that we are under an `async` scope. */
  def unsafeAssumed: AsyncToken =
    ()
}

/** Capability-safe wrapper around [[js.async]]. */
private[continuations4s] inline def async[T](body: AsyncToken ?=> T): js.Promise[T] =
  js.async(body(using ()))

/** An implementation of [[Continuations]] using JSPI async/await under
  * WebAssembly.
  *
  * @note
  *   this assumes that the root context is **already** under `JSPI.async`.
  */
private[continuations4s] trait WasmJSPISuspend(using AsyncToken) extends Continuations {
  /** The label stores a Promise that should be resolved every time the context
    * is suspended or is completed. Since Promises are one-time resolvables,
    * every resumption will "reset" the label, giving it a new Promise (see
    * [[WasmLabel.reset]]).
    *
    * Due to the promise possibly changing over time, within [[boundary]], we
    * have to dynamically resolve the reference _after_ running the `body`.
    */
  final private[WasmJSPISuspend] class WasmLabel[T] {
    var (promise, resolve) = mkPromise[T]

    def reset(): Unit = {
      val (p, q) = mkPromise[T]
      promise = p
      resolve = q
    }
  }

  /** Creates a new [[js.Promise]] and returns both the Promise and its
    * `resolve` function.
    */
  private inline def mkPromise[T]: (js.Promise[T], T => Any) = {
    var resolve: (T => Any) | Null = null
    val promise = js.Promise[T]((res, _) => resolve = res)
    (promise, resolve)
  }

  final private[WasmJSPISuspend] class WasmSuspension[-T, +R](label: Label[R], resolve: T => Any)
    extends SuspensionBase[T, R] {

    override def resume(arg: T): Boundary[R] = {
      label.reset()
      val _ = resolve(arg)
      label.promise
    }

    override def resumeAsFuture(arg: T)(using ExecutionContext): Future[R] =
      resume(arg).toFuture

    override def resumeAndForget(arg: T): Unit = {
      val _ = js.async {
        label.reset()
        val _ = resolve(arg)
      }
    }
  }

  // Implementation of the [[SuspendSupport]] interface.

  override opaque type Label[T] = WasmLabel[T]
  override opaque type Suspension[-T, +R] <: SuspensionBase[T, R] = WasmSuspension[T, R]
  override type Boundary[+R] = js.Promise[R]

  override def boundary[R](body: Label[R] ?=> R): Boundary[R] = {
    val label = WasmLabel[R]()
    val _ = js.async {
      val r = body(using label)
      label.resolve(r) // see [[WasmLabel]]
    }
    label.promise
  }

  override def boundaryAsFuture[R](body: Label[R] ?=> R)(using ExecutionContext): Future[R] =
    boundary(body).toFuture

  /** Suspends the context by creating a `js.Promise` to wait for inside the
    * [[Suspension]] class, that would be resolved once resumed.
    *
    * @note
    *   Should return immediately if resume is called from within body
    */
  override def suspend[T, R](body: Suspension[T, R] => R)(using Label[R]): T = {
    import scala.scalajs.js.wasm.JSPI.allowOrphanJSAwait
    val label = summon[Label[R]]
    val (suspPromise, suspResolve) = mkPromise[T]
    val _ = js.async {
      val suspend = WasmSuspension[T, R](label, suspResolve)
      label.resolve(body(suspend))
    }
    js.await(suspPromise)
  }
}
