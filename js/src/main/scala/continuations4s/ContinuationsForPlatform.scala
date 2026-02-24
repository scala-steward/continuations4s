package continuations4s

import continuations4s.js.AsyncToken
import continuations4s.js.WasmJSPISuspend

private[continuations4s] class ContinuationsForPlatform
  extends WasmJSPISuspend(using AsyncToken.unsafeAssumed)
