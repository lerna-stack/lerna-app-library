package lerna.util.sequence

import akka.actor.testkit.typed.scaladsl.{ LoggingTestKit, TestProbe }
import akka.actor.typed.ActorRef
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

  val sequenceId: String            = "test-sequence-id"
  val sequenceSubId: Option[String] = Option("test")

  "SequenceFactoryWorker" should {

    "採番の初項は firstValue で指定できる" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
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

    "予約した採番値が枯渇しそうな場合はあらかじめ新しい採番値を予約する" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 2,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )

      // 初期化
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          expect(result.firstValue === BigInt(3))
          expect(result.reservationAmount === 2)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = 3,     // firstValue
            maxReservedValue = 13,// firstValue + (incrementStep * (reservationAmount - 1))
          )
      }

      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      // reservationAmount が 2 なので、1回採番しても枯渇しないが、事前に採番予約が行われる
      LoggingTestKit.info("Reserving sequence: remain 1, add 1, current max reserved: 13").expect {
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))
      }

      // 事前に採番予約される
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          expect(result.maxReservedValue === BigInt(13))
          expect(result.reservationAmount === BigInt(1)) // 設定された reservationAmount - 消費済みの採番数
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo ! SequenceStore.SequenceReserved(
            maxReservedValue = BigInt(23), // maxReservedValue + (incrementStep * reservationAmount)
          )
      }

      // 事前に採番予約できた値採番値が利用できる
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(13, sequenceSubId))
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(23, sequenceSubId))

      testKit.stop(worker)
    }

    "あらかじめ新しい採番値を予約するのが失敗した場合は次の採番要求時に予約する" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 2,
            storeProbe.ref,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )

      // 初期化
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          expect(result.firstValue === BigInt(3))
          expect(result.reservationAmount === 2)
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = 3,     // firstValue
            maxReservedValue = 13,// firstValue + (incrementStep * (reservationAmount - 1))
          )
      }

      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      // reservationAmount が 2 なので、1回採番しても枯渇しないが、事前に採番予約が行われる
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))

      // 事前に採番予約されるが、失敗
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          result.replyTo ! SequenceStore.ReservationFailed
      }

      // 次に採番要求が来たとき
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(13, sequenceSubId))
      // 前回失敗した採番予約を再試行
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.ReserveSequence =>
          expect(result.maxReservedValue === BigInt(13))
          expect(result.reservationAmount === BigInt(2))
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo ! SequenceStore.SequenceReserved(
            maxReservedValue = BigInt(33), // maxReservedValue + (incrementStep * (reservationAmount - 消費済みの採番数))
          )
      }

      // 事前に採番予約できた値採番値が利用できる
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(23, sequenceSubId))
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(33, sequenceSubId))

      testKit.stop(worker)
    }

    "予約した採番値が枯渇した場合は新たに予約して採番する" in {
      val storeProbe = createTestProbe[SequenceStore.Command]()
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
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
          expect(result.reservationAmount === BigInt(1))
          expect(result.sequenceSubId === sequenceSubId)
          result.replyTo
      }
      replyTo2 ! SequenceStore.SequenceReserved(maxReservedValue = 13)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(13, sequenceSubId))

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
            sequenceId,
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
            maxReservedValue = maxReservedValue + (incrementStep * (reservationAmount - 1)),
          )
      }

      // 採番できる
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue, sequenceSubId))

      testKit.stop(worker)
    }

    "初期化時に失敗した場合は次の採番要求で再度初期化される" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 2
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
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
          // 失敗
          result.replyTo ! SequenceStore.ReservationFailed
      }

      // 採番要求：初期化のトリガーになる
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)

      // 初期化
      inside(storeProbe.receiveMessage()) {
        case result: SequenceStore.InitialReserveSequence =>
          // 成功
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = firstValue,                                                // 3
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),// 13
          )
      }

      // 採番が成功
      expect(replyToProbe.receiveMessage().value === BigInt(3))

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
            sequenceId,
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

    "採番値が枯渇しているときに予約が失敗した場合は次の採番要求で再度予約が要求される" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 1
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
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
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),// 3
          )
      }

      // 採番要求: reservationAmount が 1 なので、1回採番すると枯渇し、採番予約が行われる
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(3)) // firstValue
      }

      // 予約を失敗させる
      inside(storeProbe.receiveMessage()) {
        case reserve: SequenceStore.ReserveSequence =>
          reserve.replyTo ! SequenceStore.ReservationFailed
      }
      // 次の採番要求まで予約は行われない
      storeProbe.expectNoMessage()

      // 採番要求: 予約できていないのですぐに採番できないが、再度予約を行う
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectNoMessage()

      // 予約を成功させる
      inside(storeProbe.receiveMessage()) {
        case reserve: SequenceStore.ReserveSequence =>
          expect(reserve.maxReservedValue === BigInt(firstValue)) // 3
          expect(reserve.reservationAmount === BigInt(reservationAmount))
          expect(reserve.sequenceSubId === sequenceSubId)
          reserve.replyTo ! SequenceStore.SequenceReserved(maxReservedValue =
            reserve.maxReservedValue + (incrementStep * reserve.reservationAmount),
          )
      }

      // 予約完了後に採番される
      expect(replyToProbe.receiveMessage().value === BigInt(13))

      testKit.stop(worker)
    }

    "採番値上限を超えた値の予約は行われない" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 5
      val incrementStep     = 10
      val maxSequenceValue  = 15 // 2 回の採番で枯渇する値
      val reservationAmount = 2
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
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
          val maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1))
          expect(maxReservedValue === 15)
          result.replyTo ! SequenceStore.InitialSequenceReserved(
            initialValue = firstValue,
            maxReservedValue = BigInt(maxReservedValue),
          )
      }
      // { 5, 15 } が予約済みで、残り { 5, 15 } が採番可能

      // 採番
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue, sequenceSubId))
      }
      // { 15 } が予約済みで、残り { 15 } が採番可能
      // 採番できる値が少なくなったので通常であれば予約するが、maxSequenceValue を超えないよう予約を控える
      storeProbe.expectNoMessage()

      // 採番
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        replyToProbe.expectMessage(SequenceFactoryWorker.SequenceGenerated(firstValue + incrementStep, sequenceSubId))
      }
      // 採番できる値がないのでリセット
      storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

      testKit.stop(worker)
    }

    "採番値がoverflowしている状態で予約リトライの応答が遅れて返ってきても継続して採番できる" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 1
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
            maxSequenceValue = 13,
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
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),// 3
          )
      }

      // 採番要求: 採番予約が行われる
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(3)) // firstValue
        // 採番値が枯渇する
      }
      // 予約の応答を保留（1通目）
      val reserveSequence1 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

      // 採番要求: 採番予約が行われる
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)

      // 予約の応答を保留（2通目） - 1通目と同じ予約のリトライ
      val reserveSequence2 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

      // 予約が遅延して成功（1通目）
      reserveSequence1.replyTo ! SequenceStore.SequenceReserved(
        maxReservedValue = reserveSequence1.maxReservedValue + (incrementStep * reserveSequence1.reservationAmount),
      )
      // 採番が成功する
      expect(replyToProbe.receiveMessage().value === BigInt(13))
      // 採番値が overflow

      // リセットが要求される
      val reserveSequence3 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

      // 予約が遅延して成功（2通目）
      reserveSequence2.replyTo ! SequenceStore.SequenceReserved(
        maxReservedValue = reserveSequence2.maxReservedValue + (incrementStep * reserveSequence2.reservationAmount),
      )
      // リセット成功（3通目）
      reserveSequence3.replyTo ! SequenceStore.SequenceReset(
        maxReservedValue = reserveSequence3.firstValue + (incrementStep * (reserveSequence3.reservationAmount - 1)),
      )

      // 採番が成功する
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(3))
      }
    }

    "採番値が枯渇しているときにリトライされたリセットの応答が遅れて返ってきてもユニークな採番値を発行できる" in {
      val storeProbe        = createTestProbe[SequenceStore.Command]()
      val firstValue        = 3
      val incrementStep     = 10
      val reservationAmount = 1
      val worker = spawn(
        SequenceFactoryWorker
          .apply(
            sequenceId,
            maxSequenceValue = 13,
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
            maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1)),// 3
          )
      }

      // 採番要求: 採番予約が行われる
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(3)) // firstValue
        // 1回の採番で枯渇するので、採番値予約する
        inside(storeProbe.receiveMessage()) {
          case reserve: SequenceStore.ReserveSequence =>
            reserve.replyTo ! SequenceStore.SequenceReserved(
              maxReservedValue = reserve.maxReservedValue + (incrementStep * reserve.reservationAmount),
            )
        }
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(13)) // firstValue
        // 採番値がオーバーフローする
      }

      // リセットの応答を保留（1通目）
      val resetSequence1 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

      // 採番要求：リセットが成功するまで保留される
      val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
      replyToProbe.expectNoMessage()

      // （採番要求でリトライされた）リセットの応答を保留（2通目）
      val resetSequence2 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

      // リセット成功（1通目）
      resetSequence1.replyTo ! SequenceStore.SequenceReset(
        maxReservedValue = resetSequence1.firstValue + (incrementStep * (resetSequence1.reservationAmount - 1)),
      )
      // 採番が成功する
      expect(replyToProbe.receiveMessage().value === BigInt(3))

      // リセット成功の応答が遅れて返信（2通目）
      resetSequence2.replyTo ! SequenceStore.SequenceReset(
        maxReservedValue = resetSequence2.firstValue + (incrementStep * (resetSequence2.reservationAmount - 1)),
      )
      // 採番完了後の予約
      inside(storeProbe.receiveMessage()) {
        case reserve: SequenceStore.ReserveSequence =>
          reserve.replyTo ! SequenceStore.SequenceReserved(
            maxReservedValue = reserve.maxReservedValue + (incrementStep * reserve.reservationAmount),
          )
      }

      // 採番できる
      {
        val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
        worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
        expect(replyToProbe.receiveMessage().value === BigInt(13))
      }
    }
  }

  private val incrementStep = 10

  // GenerateSequence が届いた場合の振る舞いの網羅テスト
  // 各条件の説明などは↓を参照
  // lerna-util-sequence/src/test/resources/test-cases/picts/sequence_factory_worker_gen.pict
  "SequenceFactoryWorker (GenerateSequence)" when {
    "maxReservedValue < maxSequenceValue, nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == false" should {
      "採番する, 予約しない, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          // nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == false
          reservationAmount = 10,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue < maxSequenceValue) // 前提条件を確認: maxReservedValue < maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約しない, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue < maxSequenceValue, nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == true" should {
      "採番する, 予約する, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == true
          reservationAmount = 3,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue < maxSequenceValue) // 前提条件を確認: maxReservedValue < maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 準備: 次の予約要求で `after_isStarving == true` になるようにする
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()
        }

        {
          // 採番要求: 採番する, 予約する, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue < maxSequenceValue, nextValue < maxReservedValue, after_nextValue == maxReservedValue, after_isStarving == true" should {
      "採番する, 予約する, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          // nextValue < maxReservedValue, after_nextValue == maxReservedValue, after_isStarving == true
          reservationAmount = 2,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue < maxSequenceValue) // 前提条件を確認: maxReservedValue < maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約する, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue < maxSequenceValue, nextValue == maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true" should {
      "採番する, 予約する, リセットしない, 次の採番要求に即座に応答しない" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          // nextValue == maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue < maxSequenceValue) // 前提条件を確認: maxReservedValue < maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約する, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "maxReservedValue < maxSequenceValue, nextValue > maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true" should {
      "採番しない, 予約する, リセットしない, 次の採番要求に即座に応答しない" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          // nextValue > maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue < maxSequenceValue) // 前提条件を確認: maxReservedValue < maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          expect(replyToProbe.receiveMessage().value === maxReservedValue) // nextValue > maxReservedValue
          storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 採番しない, 予約する, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "maxReservedValue == maxSequenceValue, nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == false" should {
      "採番する, 予約しない, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(71) // maxReservedValue == maxSequenceValue
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == false
          reservationAmount = 8,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue === maxSequenceValue) // 前提条件を確認: maxReservedValue == maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約しない, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue == maxSequenceValue, nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == true" should {
      "採番する, 予約しない, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(21) // maxReservedValue == maxSequenceValue
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue < maxReservedValue, after_nextValue < maxReservedValue, after_isStarving == true
          reservationAmount = 3,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue === maxSequenceValue) // 前提条件を確認: maxReservedValue == maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約しない, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue == maxSequenceValue, nextValue < maxReservedValue, after_nextValue == maxReservedValue, after_isStarving == true" should {
      "採番する, 予約しない, リセットしない, 次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(11) // maxReservedValue == maxSequenceValue
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue < maxReservedValue, after_nextValue == maxReservedValue, after_isStarving == true
          reservationAmount = 2,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue === maxSequenceValue) // 前提条件を確認: maxReservedValue == maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約しない, リセットしない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          expect(generatedValue < maxReservedValue)                   // nextValue < maxReservedValue
          expect(generatedValue + incrementStep === maxReservedValue) // after_nextValue == maxReservedValue
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "maxReservedValue == maxSequenceValue, nextValue == maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true" should {
      "採番する, 予約しない, リセットする, 次の採番要求に即座に応答しない" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(1) // maxReservedValue == maxSequenceValue
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue == maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue === maxSequenceValue) // 前提条件を確認: maxReservedValue == maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 採番要求: 採番する, 予約しない, リセットする
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          expect(generatedValue === maxReservedValue)               // nextValue == maxReservedValue
          expect(generatedValue + incrementStep > maxReservedValue) // after_nextValue > maxReservedValue
          storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "maxReservedValue == maxSequenceValue, nextValue > maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true" should {
      "採番しない, 予約しない, リセットする, 次の採番要求に即座に応答しない" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(1) // maxReservedValue == maxSequenceValue
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 1,
          // nextValue > maxReservedValue, after_nextValue > maxReservedValue, after_isStarving == true
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        expect(maxReservedValue === maxSequenceValue) // 前提条件を確認: maxReservedValue == maxSequenceValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          expect(replyToProbe.receiveMessage().value === maxReservedValue) // nextValue > maxReservedValue
          storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 採番しない, 予約する, リセットする
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
  }

  // Storeからの ReservationResponse が届いた後の振る舞いの網羅テスト
  // 各条件の説明などは↓を参照
  // lerna-util-sequence/src/test/resources/test-cases/picts/sequence_factory_worker_store_reply.pict
  "SequenceFactoryWorker (ReservationResponse)" when {
    "isEmpty == false, isOverflow == false, msg == ReservationFailed, msg.maxReservedValue == NaN" should {
      "次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 2,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue <= maxReservedValue) // 前提条件を確認: isEmpty == false
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false
        }

        {
          // 前提条件: msg == ReservationFailed, msg.maxReservedValue == NaN
          val reserveSequence = storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          reserveSequence.replyTo ! SequenceStore.ReservationFailed
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == false, isOverflow == false, msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答する" in {
        val storeProbe = createTestProbe[SequenceStore.Command]()
        // maxReservedValue と maxSequenceValue が近くなってきたら msg.maxReservedValue == ctx.maxReservedValue になる
        val maxSequenceValue = BigInt(50)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 4,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val reserveSequence1 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val reserveSequence2 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          val maxReservedValue = replySequenceReserved(reserveSequence1).maxReservedValue

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue <= maxReservedValue) // 前提条件を確認: isEmpty == false
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false

          // 前提条件を確認: msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue
          expect(replySequenceReserved(reserveSequence2).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == false, isOverflow == false, msg == SequenceReserved, msg.maxReservedValue > ctx.maxReservedValue" should {
      "次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 2,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue <= maxReservedValue) // 前提条件を確認: isEmpty == false
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false
        }

        {
          // 前提条件: msg == SequenceReserved, msg.maxReservedValue > ctx.maxReservedValue
          val reserveSequence = storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          expect(replySequenceReserved(reserveSequence).maxReservedValue > maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == false, isOverflow == false, msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答する" in { // リセットのリトライを利用する
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(20)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 2,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          storeProbe.expectNoMessage()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val resetReserveSequence1 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          val resetReserveSequence2 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

          val maxReservedValue = replySequenceReset(resetReserveSequence1).maxReservedValue

          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue <= maxReservedValue) // 前提条件を確認: isEmpty == false
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false

          // 前提条件を確認: msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue
          expect(replySequenceReset(resetReserveSequence2).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == false, msg == ReservationFailed, msg.maxReservedValue == NaN" should {
      "次の採番要求に即座に応答しない" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue)  // 前提条件を確認: isEmpty == true
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false
        }

        {
          // 前提条件: msg == ReservationFailed, msg.maxReservedValue == NaN
          val reserveSequence = storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          reserveSequence.replyTo ! SequenceStore.ReservationFailed
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == false, msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答しない" in { // empty 状態で採番要求を続けて複数の予約をさせる
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(50)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val reserveSequence1 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          val reserveSequence2 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          val maxReservedValue = replySequenceReserved(reserveSequence1).maxReservedValue

          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue)  // 前提条件を確認: isEmpty == true
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false
          replyToProbe.expectNoMessage()

          // 前提条件を確認: msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue
          expect(replySequenceReserved(reserveSequence2).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == false, msg == SequenceReserved, msg.maxReservedValue > ctx.maxReservedValue" should {
      "次の採番要求に即座に応答する" in {
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue)  // 前提条件を確認: isEmpty == true
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false
        }

        {
          // 前提条件: msg == SequenceReserved, msg.maxReservedValue > ctx.maxReservedValue
          val reserveSequence = storeProbe.expectMessageType[SequenceStore.ReserveSequence]
          expect(replySequenceReserved(reserveSequence).maxReservedValue > maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == false, msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答しない" in { // リセットのリトライを利用する
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(20)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          replySequenceReserved(storeProbe.expectMessageType[SequenceStore.ReserveSequence])

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val resetReserveSequence1 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          val resetReserveSequence2 = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]

          val maxReservedValue = replySequenceReset(resetReserveSequence1).maxReservedValue

          val generatedValue = replyToProbe.receiveMessage().value
          replyToProbe.expectNoMessage()

          val nextValue = generatedValue + incrementStep
          expect(nextValue > maxReservedValue)  // 前提条件を確認: isEmpty == true
          expect(nextValue <= maxSequenceValue) // 前提条件を確認: isOverflow == false

          // 前提条件を確認: msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue
          expect(replySequenceReset(resetReserveSequence2).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == true, msg == ReservationFailed, msg.maxReservedValue == NaN" should {
      "次の採番要求に即座に応答しない" in { // リセットに失敗するケース
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(20)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val maxReservedValue =
            replySequenceReserved(storeProbe.expectMessageType[SequenceStore.ReserveSequence]).maxReservedValue

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          replyToProbe.expectNoMessage()

          val nextValue = generatedValue + incrementStep
          expect(nextValue > maxReservedValue) // 前提条件を確認: isEmpty == true
          expect(nextValue > maxSequenceValue) // 前提条件を確認: isOverflow == true
        }

        {
          // 前提条件: msg == ReservationFailed, msg.maxReservedValue == NaN
          val reserveSequence = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          reserveSequence.replyTo ! SequenceStore.ReservationFailed
          storeProbe.expectNoMessage()
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == true, msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答しない" in { // empty 状態で採番要求を続けて複数の予約をさせる
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(20)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence])
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
          val reserveSequence1 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
          val reserveSequence2 = storeProbe.expectMessageType[SequenceStore.ReserveSequence]

          val maxReservedValue = replySequenceReserved(reserveSequence1).maxReservedValue

          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue) // 前提条件を確認: isEmpty == true
          expect(nextValue > maxSequenceValue) // 前提条件を確認: isOverflow == true
          replyToProbe.expectNoMessage()

          // 前提条件を確認: msg == SequenceReserved, msg.maxReservedValue == ctx.maxReservedValue
          expect(replySequenceReserved(reserveSequence2).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答しない
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.expectNoMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == true, msg == SequenceReset, msg.maxReservedValue < ctx.maxReservedValue" should {
      "次の採番要求に即座に応答する" in { // 通常のリセットのケース
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(9999)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(
            storeProbe.expectMessageType[SequenceStore.InitialReserveSequence],
            prevMaxReservedValue = Option(BigInt(9983)),
          ).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue) // 前提条件を確認: isEmpty == true
          expect(nextValue > maxSequenceValue) // 前提条件を確認: isOverflow == true
        }

        {
          // 前提条件を確認: msg == SequenceReset, msg.maxReservedValue < ctx.maxReservedValue
          val resetReserveSequence = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          expect(replySequenceReset(resetReserveSequence).maxReservedValue < maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
    "isEmpty == true, isOverflow == true, msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue" should {
      "次の採番要求に即座に応答しない" in { // 1回の初期化で maxSequenceValue に到達するケース
        val storeProbe       = createTestProbe[SequenceStore.Command]()
        val maxSequenceValue = BigInt(10)
        val worker = createWorker(
          maxSequenceValue = maxSequenceValue,
          firstValue = 3,
          reservationAmount = 1,
          storeProbe,
        )

        // 初期化
        val maxReservedValue =
          initialize(storeProbe.expectMessageType[SequenceStore.InitialReserveSequence]).maxReservedValue
        storeProbe.expectNoMessage()

        {
          // 準備
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          val generatedValue = replyToProbe.receiveMessage().value
          val nextValue      = generatedValue + incrementStep
          expect(nextValue > maxReservedValue) // 前提条件を確認: isEmpty == true
          expect(nextValue > maxSequenceValue) // 前提条件を確認: isOverflow == true
        }

        {
          // 前提条件を確認: msg == SequenceReset, msg.maxReservedValue == ctx.maxReservedValue
          val resetReserveSequence = storeProbe.expectMessageType[SequenceStore.ResetReserveSequence]
          expect(replySequenceReset(resetReserveSequence).maxReservedValue === maxReservedValue)
        }

        {
          // 次の採番要求に即座に応答する
          val replyToProbe = createTestProbe[SequenceFactoryWorker.SequenceGenerated]()
          worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId, replyToProbe.ref)
          replyToProbe.receiveMessage()
        }
      }
    }
  }

  private def createWorker(
      maxSequenceValue: BigInt,
      firstValue: BigInt,
      reservationAmount: Int,
      storeProbe: TestProbe[SequenceStore.Command],
  ): ActorRef[SequenceFactoryWorker.Command] = {
    spawn(
      SequenceFactoryWorker.apply(
        sequenceId,
        maxSequenceValue = maxSequenceValue,
        firstValue = firstValue,
        incrementStep = incrementStep,
        reservationAmount = reservationAmount,
        storeProbe.ref,
        idleTimeout = 10.seconds,
        sequenceSubId,
      ),
    )
  }

  private def initialize(
      msg: SequenceStore.InitialReserveSequence,
      prevMaxReservedValue: Option[BigInt] = None,
  ): SequenceStore.InitialSequenceReserved = {
    val initialValue = prevMaxReservedValue.map(_ + incrementStep).getOrElse(msg.firstValue)
    val reply = SequenceStore.InitialSequenceReserved(
      initialValue = initialValue,
      maxReservedValue = initialValue + (incrementStep * (msg.reservationAmount - 1)),
    )
    msg.replyTo ! reply
    reply
  }

  private def replySequenceReserved(msg: SequenceStore.ReserveSequence): SequenceStore.SequenceReserved = {
    val reply = SequenceStore.SequenceReserved(
      maxReservedValue = msg.maxReservedValue + (incrementStep * msg.reservationAmount),
    )
    msg.replyTo ! reply
    reply
  }

  private def replySequenceReset(msg: SequenceStore.ResetReserveSequence): SequenceStore.SequenceReset = {
    val reply = SequenceStore.SequenceReset(
      maxReservedValue = msg.firstValue + (incrementStep * (msg.reservationAmount - 1)),
    )
    msg.replyTo ! reply
    reply
  }

}
