# Lerna Util Akka

*Lerna Util Akka* library provides some utilities related [Akka Classic](https://doc.akka.io/docs/akka/current/index-classic.html).

## AtLeastOnceDelivery

`AtLeastOnceDelivery` is an object that provides reliable delivery features for *Akka Classic*.
If you work on *Akka Typed*, we recommend using the [Official Reliable Delivery](https://doc.akka.io/docs/akka/current/typed/reliable-delivery.html).
`AtLeastOnceDelivery` provides two methods `askTo` and `tellTo`.

`AtLeastOnceDelivery.askTo` provides a similar feature provided by `akka.pattern.ask`, but it has a retry mechanism.
We can use `AtLeastOnceDelivery.askTo` like below.
For more details, see a Scaladoc.

```scala mdoc:compile-only
import lerna.util.akka.AtLeastOnceDelivery
import lerna.util.tenant.Tenant
import lerna.util.trace.{ TraceId, RequestContext }

import akka.actor.{ ActorSystem, ActorRef }
import akka.testkit.TestActors
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

implicit val system = ActorSystem("system")
implicit val timeout: Timeout = 3.seconds
val echoActor: ActorRef = system.actorOf(TestActors.echoActorProps)

implicit val ctx = new RequestContext {
    def traceId: TraceId = TraceId.unknown
    def tenant: Tenant = new Tenant { val id = "" }
}
val response: Future[Any] = AtLeastOnceDelivery.askTo(echoActor, "message")
```

`AtLeastOnceDelivery.tellTo` provides a simiar feature provided by Akka's `tell`, but it has a retry mechanism.
We can use `AtLeastOnceDelivery.tellTo` like below.
For more details, see a Scaladoc.

```scala mdoc:compile-only
import lerna.util.akka.AtLeastOnceDelivery
import lerna.util.tenant.Tenant
import lerna.util.trace.{ TraceId, RequestContext }

import akka.actor.{ ActorSystem, ActorRef }
import akka.testkit.TestActors

implicit val system = ActorSystem("system")
val echoActor: ActorRef = system.actorOf(TestActors.echoActorProps)

implicit val ctx = new RequestContext {
    def traceId: TraceId = TraceId.unknown
    def tenant: Tenant = new Tenant { val id = "" }
}
AtLeastOnceDelivery.tellTo(echoActor, "message")
```


## ProcessingTimeout
🚧 UNDER CONSTRUCTION 🚧


## FailureSkipFlow
`FailureSkipFlow` is an *Akka Stream* graph processing operator that provides reporting and skipping Failure in a stream.
 The FailureSkipFlow skips failures occurred in the underlying flow, but report the failures to onFailure.
 It is useful you can skip failures but want to take some actions (like logging) against failures.

```scala mdoc:compile-only
import lerna.util.akka.stream.FailureSkipFlow
import akka.actor.ActorSystem
import akka.NotUsed
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }

import scala.concurrent._
import scala.concurrent.duration._

implicit val system = ActorSystem("my-system")
val elements: collection.immutable.Seq[Int] = 1 to 5
var failureElements: Seq[Int] = Seq.empty

val originalFlow: Flow[Int, Int, NotUsed] = Flow[Int].map { i =>
  if (i == 3 || i == 4) throw new RuntimeException("bang")
  i
}
val flow: Flow[Int, Int, NotUsed] = FailureSkipFlow(originalFlow) { (element, ex) =>
  failureElements :+= element
}
val result: Future[Seq[Int]] = Source(elements).via(flow).runWith(Sink.seq[Int])
val seq: Seq[Int] = Await.result(result, 1.second)

// seq == Seq(1,2,5)
// failureElements == Seq(3,4)
```
