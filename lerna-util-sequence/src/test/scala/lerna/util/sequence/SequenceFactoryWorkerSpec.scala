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

    "予約に失敗した場合は次の採番要求で再度予約が要求される" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 2
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = 999,
            firstValue = firstValue,
            incrementStep = incrementStep,
            reservationAmount = reservationAmount,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )
      // 初期化
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = firstValue,                                                // 3
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),// 13
          )
      }

      // 採番要求: reservationAmount が 2 なので、2回採番すると枯渇 → 予約
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(3)) // firstValue
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(13)) // firstValue + incrementStep
      }

      // 予約を失敗させる
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          result.replyTo ! SequenceStore.ReservationFailed
      }

      // 採番要求: 予約できていないのでレスポンスは来ない
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectNoMessage()

      // 再度予約が要求される
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          expect(result.maxReservedValue === BigInt(firstValue + incrementStep)) // 13
          expect(result.reservationAmount === reservationAmount)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo ! SequenceStore.SequenceReserved(
            maxReservedValue = result.maxReservedValue + incrementStep * reservationAmount,
          )
      }

      testKit.stop(worker)
    }

    "初期化時に上限を超えた場合はリセットする" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val maxSequenceValue  = 999
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 1
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = maxSequenceValue,
            firstValue = firstValue,
            incrementStep = incrementStep,
            reservationAmount = reservationAmount,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )

      // maxSequenceValue = 999 より大きい値を返す。 ※ この値が実際に発生するとは限らない
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = maxSequenceValue + 1,
            maxReservedValue = maxSequenceValue + 1 + (incrementStep * (reservationAmount - 1)),
          )
      }

      // store に保存されていた initialValue が maxSequenceValue より大きい場合、リセットされる
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ResetReserveSequence =>
          expect(result.firstValue === BigInt(firstValue))
          expect(result.reservationAmount === reservationAmount)
          expect(result.sequenceSubId === sequenceSubId)

          val maxReservedValue = firstValue
          result.replyTo ! SequenceStore.SequenceReset(
            maxReservedValue = maxReservedValue + (incrementStep * reservationAmount),
          )
      }

      // 採番できる
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue, sequenceSubId))

      testKit.stop(worker)
    }

    "採番要求で次番号が上限を超えた場合はリセットする" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val maxSequenceValue  = 12 // < 13 = 3 + 10 = firstValue + incrementStep
      val reservationAmount = 1
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = maxSequenceValue,
            firstValue = firstValue,
            incrementStep = incrementStep,
            reservationAmount = reservationAmount,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )

      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = firstValue,
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),
          )
      }

      // 採番できる
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue, sequenceSubId))

      // newNextValue > maxSequenceValue の場合、リセットされる
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ResetReserveSequence =>
          expect(result.firstValue === BigInt(firstValue))
          expect(result.reservationAmount === reservationAmount)
          expect(result.sequenceSubId === sequenceSubId)
      }

      testKit.stop(worker)
    }

    "予約中の採番要求で次番号が上限を超えた場合はリセットする" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val maxSequenceValue  = 15 // 3 + 10 = 13 < 15 < 23 = 3 + 10 * 2
      val reservationAmount = 2
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            maxSequenceValue = maxSequenceValue,
            firstValue = firstValue,
            incrementStep = incrementStep,
            reservationAmount = reservationAmount,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )

      // 初期化
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = firstValue,
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),
          )
      }

      // 採番できる 1
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue, sequenceSubId))
      }

      storeProbe.receiveMessage() shouldBe a[SequenceStore.ReserveSequence]

      // 採番できる 2
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue + incrementStep, sequenceSubId))
      }

      // 採番要求
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      }

      // newNextValue > maxSequenceValue の場合、リセットされる
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ResetReserveSequence =>
          expect(result.firstValue === BigInt(firstValue))
          expect(result.reservationAmount === reservationAmount)
          expect(result.sequenceSubId === sequenceSubId)
      }

      testKit.stop(worker)
    }
  }

}
