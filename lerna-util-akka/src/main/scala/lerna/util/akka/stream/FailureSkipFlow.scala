package lerna.util.akka.stream

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

/** An object that provides factory methods of [[FailureSkipFlow]]
  */
object FailureSkipFlow {

  /** Create a [[FailureSkipFlow]] for underlying `flow` operator
    *
    * The underlying flow (called `flow`) can be given an arbitrary flow.
    * The [[FailureSkipFlow]] skips failures occurred in the underlying flow, but report the failures to `onFailure`.
    * It is useful that you can skip failures but want to take some actions (like logging) against failures.
    *
    * @param flow The underlying flow
    * @param onFailure The handler of failures occurred in the underlying flow
    */
  def apply[In, Out](flow: Flow[In, Out, _])(onFailure: (In, Throwable) => Unit): Flow[In, Out, NotUsed] = {
    Flow.fromGraph(new FailureSkipFlow(flow)(onFailure))
  }
}

/** An Akka Stream graph processing operator that provides reporting and skipping Failure in a stream
  *
  * @param flow The underlying flow
  * @param onFailure The handler of failure
  * @tparam In The type of flow input
  * @tparam Out The type of flow output
  */
final class FailureSkipFlow[In, Out](flow: Flow[In, Out, _])(onFailure: (In, Throwable) => Unit)
    extends GraphStage[FlowShape[In, Out]] {

  private val stageName = getClass.getSimpleName

  private val in  = Inlet[In](s"$stageName.in")
  private val out = Outlet[Out](s"$stageName.out")

  override def shape: FlowShape[In, Out] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = FailureSkipFlowLogic

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  object FailureSkipFlowLogic extends GraphStageLogicWithLogging(shape) {

    private var processingElement: Option[In] = None

    private var pendingPull: Boolean = false

    private def processing: Boolean = processingElement.isDefined

    private def saveProcessingElement(elem: In): Unit = {
      processingElement = Option(elem)
    }

    private def resetProcessingElement(): Unit = {
      processingElement = None
    }

    // set empty handler
    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = ()
      },
    )
    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = ()
      },
    )

    override def preStart(): Unit = startGraph()

    private def createSinkIn(): SubSinkInlet[Out] = {
      val sinkIn = new SubSinkInlet[Out](s"$stageName.subIn")
      sinkIn.setHandler(new InHandler {
        override def onPush(): Unit = {
          if (pendingPull) {
            log.debug("in <-pull- this")
            tryPull(in)
            pendingPull = false
          }
          val element = sinkIn.grab()
          log.debug(s"sub-sink  -push-> out: $element")
          push(out, element)
          resetProcessingElement()
        }

        override def onUpstreamFinish(): Unit = {
          log.debug(s"sub-sink: onUpstreamFinish")
          complete(out)
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          log.debug(s"sub-sink: onUpstreamFailure")
          processingElement.foreach { elem =>
            onFailure(elem, ex)
          }
          startGraph()
        }
      })

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit = {
            log.debug(s"sub-sink <-pull- out")
            sinkIn.pull()
          }

          override def onDownstreamFinish(cause: Throwable): Unit = {
            log.debug(s"onDownstreamFinish")
            sinkIn.cancel(cause)
          }
        },
      )
      sinkIn
    }

    private def createSourceOut(): SubSourceOutlet[In] = {
      val sourceOut = new SubSourceOutlet[In](s"$stageName.subOut")
      sourceOut.setHandler(new OutHandler {
        override def onPull(): Unit = {
          // back pressure if flow is processing
          if (!hasBeenPulled(in) && !processing) {
            log.debug(s"in <-pull- sub-source")
            tryPull(in)
          } else {
            log.debug(s"in <-pull- sub-source: ignore")
            pendingPull = true
          }
        }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          log.debug(s"sub-source: onDownstreamFinish")
          if (isClosed(out)) {
            // sub-source is finish when sub-flow is failure.
            // However maybe outer source can be open.
            cancel(in, cause)
          }
        }
      })

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val element = grab(in)
            log.debug(s"in  -push-> sub-source: $element")
            sourceOut.push(element)
            // save the element to log when sub-flow failed
            saveProcessingElement(element)
          }

          override def onUpstreamFinish(): Unit = {
            log.debug(s"onUpstreamFinish")
            sourceOut.complete()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            log.debug(s"onUpstreamFailure")
            fail(out, ex)
          }
        },
      )
      sourceOut
    }

    private def startGraph(): Unit =
      if (isClosed(in)) {
        // complete immediately when input already closed
        // ex) Processing last element failed
        complete(out)
      } else {
        val sourceOut = createSourceOut()
        val sinkIn    = createSinkIn()

        log.debug("starting graph...")
        resetProcessingElement()
        Source
          .fromGraph(sourceOut.source)
          .via(flow)
          .runWith(sinkIn.sink)(subFusingMaterializer)

        if (isAvailable(out)) {
          sinkIn.pull()
        }
      }
  }
}
