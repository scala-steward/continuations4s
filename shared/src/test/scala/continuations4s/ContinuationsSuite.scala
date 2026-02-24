package continuations4s

import continuations4s.Continuations.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.util.Try

def await[T, R](future: Future[T])(using Label[Unit]): T = {
  val result = suspend[Try[T], Unit] { sus =>
    future.onComplete { value =>
      sus.resume(value)
    }
  }
  result.get
}

class ContinuationsSuite extends munit.FunSuite {
  override def munitTimeout: Duration =
    10.seconds

  def await[T, R](future: Future[T])(using Label[Unit]): T = {
    val result = suspend[Try[T], Unit] { sus =>
      future.onComplete { value =>
        sus.resume(value)
      }
    }
    result.get
  }

  def runToFuture[T](body: Label[Unit] ?=> T): Future[T] = {
    val p = Promise[T]()
    val _ = boundary[Unit] {
      val r = body
      p.success(r)
    }
    p.future
  }

  test("convert simple future to direct style") {
    runToFuture {
      val f = Future(1 + 1)
      val r = await(f)
      assertEquals(r, 2)
    }
  }

  test("boundary with no suspend") {
    val res = boundaryAsFuture[Int] {
      val x = 1
      val y = 2
      x + y
    }
    for {
      r <- res
    } yield assertEquals(r, 3)
  }

  test("boundary can suspend") {
    val res = boundaryAsFuture[Int] {
      val x = 1
      suspend[Unit, Int](_ => x + 1)
      ???
    }
    for {
      r <- res
    } yield assertEquals(r, 2)
  }

  test("boundary can suspend with immediate resume") {
    val r = boundaryAsFuture[Future[Int]] {
      Future.successful(
        1 + suspend[Int, Future[Int]](r => r.resumeAsFuture(2).flatten) +
          suspend[Int, Future[Int]](r => r.resumeAsFuture(3).flatten) + 4
      )
    }
    r.flatten.map { r =>
      assertEquals(r, 10)
    }
  }

  test("immediate resume does not deadlock") {
    boundaryAsFuture[Unit] {
      val _ = suspend[Int, Unit](_.resume(1): Unit)
      val _ = suspend[Int, Unit](_.resume(2): Unit)
      val _ = suspend[Int, Unit](_.resume(3): Unit)
    }
  }

  test("boundary suspend can communicate") {
    case class Iter(n: Int, nx: Int => Future[Iter])
    val r0 = boundaryAsFuture[Iter] {
      var r = 0
      while (true)
        r += suspend[Int, Iter](cb => Iter(r, cb.resumeAsFuture))
      ???
    }
    for {
      r0 <- r0
      _ = assertEquals(r0.n, 0)
      r1 <- r0.nx(2)
      _ = assertEquals(r1.n, 2)
      r2 <- r1.nx(3)
      _ = assertEquals(r2.n, 5)
    } yield ()
  }

  test("basic") {
    enum Response[T] {
      case Next(nx: () => Future[Response[T]], v: T)
      case End(v: T)
    }
    import Response.*

    val oneThenTwo = boundaryAsFuture[Response[Int]] {
      suspend[Unit, Response[Int]](sus => Next(() => sus.resumeAsFuture(()), 1))
      End(2)
    }

    oneThenTwo.map {
      case Next(nx, v) =>
        assertEquals(v, 1)
        nx().map(v2 => assertEquals(v2, End(2)))
      case End(_) =>
        fail("expected Next")
    }.flatten
  }

  test("fibonacci") {
    case class Seqnt[T, R](v: T, nx: R => Future[Seqnt[T, R]])
    type Seqn[T] = Seqnt[T, Int]

    def fib =
      boundaryAsFuture[Seqn[Int]] {
        var a = 1
        var b = 1
        while (true) {
          val steps = suspend[Int, Seqn[Int]](c => Seqnt(a, c.resumeAsFuture))
          for (_ <- 1 to steps) {
            val c = a + b
            a = b
            b = c
          }
        }
        Seqnt(0, _ => Future.failed(new IllegalStateException("unreachable")))
      }

    for {
      first <- fib
      fibs <- (1 to 10).foldLeft(Future.successful(Vector(first))) { (accF, _) =>
        for {
          acc <- accF
          next <- acc.last.nx(1)
        } yield acc :+ next
      }
    } yield assertEquals(fibs.map(_.v).toList, List(1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89))
  }
}
