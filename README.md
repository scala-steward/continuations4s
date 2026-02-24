# Continuations4s

Low-level, platform-specific, delimited continuations, implemented for Scala 3,
with support for JVM, ScalaJS, and Scala Native. Needed to support
cross-platform, "direct style" libraries.

Implementation notes:
- Scala Native has baked-in delimited continuations support;
- ScalaJS, using the WASM backend, can use [WASM JSPI](https://v8.dev/blog/jspi);
- On the JVM the implementation blocks threads, which is cheaper to do since [Java 21's virtual threads](https://openjdk.org/jeps/444).

Extracted from the [lampepfl/gears](https://github.com/lampepfl/gears) project.

## Usage

```scala ignore
libraryDependencies += "org.funfix" %%% "continuations4s" % "0.1.0"
```

Example — this works across JVM, ScalaJS (WASM) and Scala Native (executable [Scala CLI](https://scala-cli.virtuslab.org/) snippet):

```scala raw
//> using scala "3.8.1"
//> using dep "org.funfix::continuations4s::0.1.0"

import continuations4s.Continuations
import scala.concurrent.*
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

type Async = Continuations.Label[Unit]

/** For semantically blocking on the result of a `Future`. */
def await[T, R](future: Future[T])(using Async): T = {
  val result: Try[T] = 
    Continuations.suspend[Try[T], Unit] { sus =>
      future.onComplete { value =>
        sus.resume(value)
      }
    }
  result.get
}

/** Utility for interop - can compose a `Future` via delimited continuations. */
def runToFuture[T](body: Async ?=> T): Future[T] = {
  val p = Promise[T]()
  val _ = Continuations.boundary[Unit] {
    val r = body
    p.success(r)
  }
  p.future
}

/** End-of-the world execution, to use in `main`. */
def runBlocking[T](body: Async ?=> T): T = {
  import java.util.concurrent.CountDownLatch
  var result: Option[Try[T]] = None
  val latch = new CountDownLatch(1)

  Continuations.boundary[Unit] {
    result = Some(Try(body))
    latch.countDown()
  }
  latch.await()
  result.get.get
}

@main def run() = runBlocking {
  val f1 = Future(1 + 1) // executed async
  val r1 = await(f1) // semantic blocking

  val f2 = Future(10 * 2) // executed async
  val r2 = await(f2) // semantic blocking

  val sum = r1 + r2
  println(s"Sum: $sum")
}
```

You can run it plainly, via the JVM:

```bash
scala --enable-markdown \
  https://raw.githubusercontent.com/funfix/continuations4s/refs/heads/main/README.md
```

And you can also execute that via Scala Native to see it in action:

```bash
scala \
  --native \
  --native-version 0.5.10 \
  --enable-markdown \
  https://raw.githubusercontent.com/funfix/continuations4s/refs/heads/main/README.md
```

See **[API documentation](https://continuations4s.funfix.org/continuations4s.html)**.

## License

Copyright 2024-2026 LAMP, EPFL.
Copyright 2026 Alexandru Nedelcu.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
