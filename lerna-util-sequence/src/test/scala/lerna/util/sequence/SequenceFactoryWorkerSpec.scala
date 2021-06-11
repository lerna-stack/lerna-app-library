package lerna.util.sequence

import com.typesafe.config.ConfigFactory
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.Inside

import scala.concurrent.duration._

object SequenceFactoryWorkerSpec {

  private val config = ConfigFactory.parseString("""
    | akka.actor {
    |   provider = local
    | }
    """.stripMargin)
}

class SequenceFactoryWorkerSpec
    extends ScalaTestWithTypedActorTestKit(SequenceFactoryWorkerSpec.config)
    with LernaBaseSpec
    with Inside {

  private implicit val tenant: Tenant = new Tenant {
    override def id: String = "dummy"
  }

  val sequenceSubId: Option[String] = Option("test")

  "SequenceFactoryWorker" should {

    "採番の初項は firstValue で指定できる" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 11,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )
      // reservationAmount の個数だけ採番できるように予約
      val replyTo = inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          expect(result.firstValue === BigInt(3))
          expect(result.reservationAmount === 11)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo
      }
      replyTo ! SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 113)

      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))

      testKit.stop(worker)
    }

    "予約した採番値が枯渇した場合は新たに予約して採番する" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 1,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )
      val replyTo1 = inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          expect(result.firstValue === BigInt(3))
          expect(result.reservationAmount === 1)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo
      }
      replyTo1 ! SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 3)

      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      // reservationAmount が 1 なので、1回採番すると枯渇
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))

      // 枯渇した場合は新たな採番値を予約して採番
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      val replyTo2 = inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          expect(result.maxReservedValue === BigInt(3))
          expect(result.reservationAmount === 1)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo
      }
      replyTo2 ! SequenceStore.SequenceReserved(maxReservedValue = 13)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(13, sequenceSubId))

      testKit.stop(worker)
    }
  }

}
