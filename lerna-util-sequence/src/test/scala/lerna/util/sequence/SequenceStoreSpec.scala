package lerna.util.sequence

import com.typesafe.config.ConfigFactory
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant

import java.util.UUID

object SequenceStoreSpec {
  private implicit val tenant: Tenant = new Tenant {
    override def id: String = "dummy"
  }

  private val config = ConfigFactory
    .parseString(s"""
    | akka.actor {
    |   provider = local
    | }
    | lerna.util.sequence {
    |   cassandra.tenants.${tenant.id} = $${lerna.util.sequence.cassandra.default}
    | }
    """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved())
    .resolve()
}

class SequenceStoreSpec extends ScalaTestWithTypedActorTestKit(SequenceStoreSpec.config) with LernaBaseSpec {

  import SequenceStoreSpec.tenant

  private[this] lazy val cassandraConfig = new SequenceFactoryConfig(system.settings.config).cassandraConfig

  private lazy val session =
    CassandraSessionProvider.connect(system, cassandraConfig)(system.executionContext).futureValue

  override def beforeAll(): Unit = {
    super.beforeAll()
    SequenceTestkit.dropKeyspaceIfExists(session, cassandraConfig)
  }

  override def afterAll(): Unit = {
    try session.close()
    finally super.afterAll()
  }

  "SequenceStore" should {

    val sequenceSubId = Option("test")

    "最初の予約では初項（firstValue）が初期値（initialValue）となり、reservationAmount で指定した分だけの採番値が予約される" in {
      val store = spawn(
        SequenceStore.apply(sequenceId = generateUniqueId(), nodeId = 1, incrementStep = 3, cassandraConfig),
      )
      val testProbe = createTestProbe[SequenceStore.ReservationResponse]()

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 3,
        reservationAmount = 10,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        // maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1))
        SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 30),
      )

      testKit.stop(store)
    }

    "reservationAmount が 1 の場合は初項（firstValue）が予約済み最大値（maxReservedValue）となる" in {
      val store = spawn(
        SequenceStore.apply(sequenceId = generateUniqueId(), nodeId = 1, incrementStep = 3, cassandraConfig),
      )
      val testProbe = createTestProbe[SequenceStore.ReservationResponse]()

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 3,
        reservationAmount = 1,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        // maxReservedValue = firstValue + (incrementStep * (reservationAmount - 1))
        SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 3),
      )

      testKit.stop(store)
    }

    "過去すでに予約された実績があれば、次の初期値は過去実績より１つ進んだ値になる" in {
      val store = spawn(
        SequenceStore.apply(sequenceId = generateUniqueId(), nodeId = 1, incrementStep = 3, cassandraConfig),
      )
      val testProbe = createTestProbe[SequenceStore.ReservationResponse]()

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        // maxReservedValue = (直前の maxReservedValue + incrementStep) + (incrementStep * (reservationAmount - 1))
        SequenceStore
          .InitialSequenceReserved(initialValue = 304, maxReservedValue = 604),
      )

      testKit.stop(store)
    }

    "予約した採番値の最大値を maxReservedValue として返す" in {
      val store = spawn(
        SequenceStore.apply(sequenceId = generateUniqueId(), nodeId = 1, incrementStep = 3, cassandraConfig),
      )
      val testProbe = createTestProbe[SequenceStore.ReservationResponse]()

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      store ! SequenceStore.ReserveSequence(
        maxReservedValue = 301,
        reservationAmount = 100,
        sequenceSubId,
        testProbe.ref,
      )
      // maxReservedValue = 直前の maxReservedValue + (incrementStep * reservationAmount)
      testProbe.expectMessage(SequenceStore.SequenceReserved(maxReservedValue = 601))

      testKit.stop(store)
    }

    "再起動したときに保存した予約値が復元できる" in {
      val sequenceId    = generateUniqueId()
      val nodeId        = 1
      val incrementStep = 3
      val store1        = spawn(SequenceStore.apply(sequenceId, nodeId, incrementStep, cassandraConfig))
      val testProbe     = createTestProbe[SequenceStore.ReservationResponse]()

      store1 ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      testKit.stop(store1)

      val store2 = spawn(SequenceStore.apply(sequenceId, nodeId, incrementStep, cassandraConfig))

      store2 ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        // 前回の maxReservedValue を基準に initialValue が決まる
        SequenceStore
          .InitialSequenceReserved(initialValue = 304, maxReservedValue = 604),
      )

      testKit.stop(store2)
    }

    "障害が発生しても継続して予約できる" ignore { // FIXME: typed 化で同じ仕組みではtestできなくなった
      val store = spawn(
        SequenceStore.apply(sequenceId = generateUniqueId(), nodeId = 1, incrementStep = 3, cassandraConfig),
      )
      val testProbe = createTestProbe[SequenceStore.ReservationResponse]()

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // 擬似的に障害を起こす
      store ! ??? // Status.Failure(new RuntimeException("bang!"))

      store ! SequenceStore.ReserveSequence(
        maxReservedValue = 301,
        reservationAmount = 100,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.SequenceReserved(maxReservedValue = 601))

      testKit.stop(store)
    }
  }

  def generateUniqueId(): String = {
    UUID.randomUUID().toString
  }
}
