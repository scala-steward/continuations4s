package continuations4s
package jvm

import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.blocking

private[continuations4s] trait VThreadSuspend extends Continuations {

  final private[VThreadSuspend] class VThreadLabel[R] {
    private var result: Option[R] = None
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    private[VThreadSuspend] def clearResult(): Unit = {
      lock.lock()
      result = None
      lock.unlock()
    }

    private[VThreadSuspend] def setResult(data: R): Unit = {
      lock.lock()
      try {
        result = Some(data)
        cond.signalAll()
      } finally lock.unlock()
    }

    private[VThreadSuspend] def waitResult(): R = {
      lock.lock()
      try {
        while (result.isEmpty) cond.await()
        result.get
      } finally lock.unlock()
    }
  }

  override opaque type Label[R] = VThreadLabel[R]
  override type Boundary[+R] = R

  // outside boundary: waiting on label
  //  inside boundary: waiting on suspension
  final private[VThreadSuspend] class VThreadSuspension[-T, +R](
    using private[VThreadSuspend] val l: Label[R] @uncheckedVariance
  ) extends SuspensionBase[T, R] {
    private var nextInput: Option[T] = None
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    private[VThreadSuspend] def setInput(data: T): Unit = {
      lock.lock()
      try {
        nextInput = Some(data)
        cond.signalAll()
      } finally lock.unlock()
    }

    // variance is safe because the only caller created the object
    private[VThreadSuspend] def waitInput(): T @uncheckedVariance = {
      lock.lock()
      try {
        while (nextInput.isEmpty) cond.await()
        nextInput.get
      } finally lock.unlock()
    }

    override def resume(arg: T): Boundary[R] = {
      l.clearResult()
      setInput(arg)
      l.waitResult()
    }

    override def resumeAsFuture(arg: T)(using ExecutionContext): Future[R] =
      Future(blocking(resume(arg)))

    override def resumeAndForget(arg: T): Unit = {
      l.clearResult()
      setInput(arg)
    }
  }

  override opaque type Suspension[-T, +R] <: SuspensionBase[T, R] =
    VThreadSuspension[T, R]

  override def boundary[R](body: Label[R] ?=> R): R = {
    val label = VThreadLabel[R]()
    executor.execute { () =>
      val result: R = body(using label)
      label.setResult(result)
    }
    label.waitResult()
  }

  override def boundaryAsFuture[R](body: Label[R] ?=> R)(using ExecutionContext): Future[R] =
    Future(blocking(boundary(body)))

  override def suspend[T, R](body: this.Suspension[T, R] => R)(using Label[R]): T = {
    val sus = new VThreadSuspension[T, R]()
    // asynchronous execution is required to avoid deadlock when resume
    // is called from the same thread that is executing the boundary
    executor.execute { () =>
      val res = body(sus)
      summon[Label[R]].setResult(res)
    }
    sus.waitInput()
  }

  private lazy val executor =
    Executors.newThreadPerTaskExecutor(
      Thread
        .ofVirtual()
        .name("continuations4s.VThread-", 0L)
        .factory()
    )
}
