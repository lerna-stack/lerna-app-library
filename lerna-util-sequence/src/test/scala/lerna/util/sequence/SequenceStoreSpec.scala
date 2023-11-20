package lerna.util.sequence

import akka.actor.ClassicActorSystemProvider
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.datastax.oss.driver.api.core.connection.{ ClosedConnectionException, HeartbeatException }
import com.datastax.oss.driver.api.core.{
  AllNodesFailedException,
  ConsistencyLevel,
  CqlSession,
  DriverTimeoutException,
}
import com.datastax.oss.driver.api.core.cql.{ AsyncResultSet, Statement }
import com.datastax.oss.driver.api.core.metadata.Node
import com.datastax.oss.driver.api.core.servererrors._
import com.typesafe.config.ConfigFactory
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.net.InetAddress
import java.util
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

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
    CqlSessionProvider.connect(system, cassandraConfig).futureValue(Timeout(15.seconds))

  override def beforeAll(): Unit = {
    super.beforeAll()
    SequenceTestkit.dropKeyspaceIfExists(session, cassandraConfig)
  }

  override def afterAll(): Unit = {
    try session.close()
    finally super.afterAll()
  }

  private trait Fixture {
    val testProbe: TestProbe[SequenceStore.ReservationResponse] =
      createTestProbe[SequenceStore.ReservationResponse]()

    lazy val sequenceSubId: Option[String] = Option("test")
    lazy val sequenceId: String            = UUID.randomUUID().toString

    def spawnSequenceStore(
        nodeId: Int,
        incrementStep: Int,
    ): ActorRef[SequenceStore.Command] = {
      spawn(SequenceStore(sequenceId, nodeId, incrementStep, cassandraConfig))
    }

    def spawnSequenceStore(
        nodeId: Int,
        incrementStep: Int,
        executor: CqlStatementExecutor,
    ): ActorRef[SequenceStore.Command] = {
      spawn(SequenceStore(sequenceId, nodeId, incrementStep, cassandraConfig, executor = executor))
    }
  }

  private trait CqlStatementExecutorStubFixture {
    private val executeAsyncFailureRef = new AtomicReference[Option[Throwable]](None)

    val executorStub: CqlStatementExecutor = new CqlStatementExecutor {
      override def executeAsync[T <: Statement[T]](
          statement: Statement[T],
      )(implicit session: CqlSession): Future[AsyncResultSet] = {
        executeAsyncFailureRef.getAndSet(None) match {
          case Some(cause) => Future.failed(cause)
          case None        => CqlStatementExecutor.executeAsync(statement)(session)
        }
      }
    }

    def failNextExecuteAsync(cause: Throwable): Unit = {
      executeAsyncFailureRef.set(Option(cause))
    }
  }

  "SequenceStore" should {

    "最初の予約では初項（firstValue）が初期値（initialValue）となり、reservationAmount で指定した分だけの採番値が予約される" in new Fixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3)

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

    "reservationAmount が 1 の場合は初項（firstValue）が予約済み最大値（maxReservedValue）となる" in new Fixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3)

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

    "過去すでに予約された実績があれば、次の初期値は過去実績より１つ進んだ値になる" in new Fixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3)

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

    "予約した採番値の最大値を maxReservedValue として返す" in new Fixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3)

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

    "リセット時は reservationAmount で指定した分だけの採番値が予約される" in new Fixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3)

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      store ! SequenceStore.ResetReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      // reservationAmount = ((maxReservedValue - firstValue) / incrementStep) + 1
      // 101 = ((301 - 1) / 3) + 1
      testProbe.expectMessage(SequenceStore.SequenceReset(maxReservedValue = 301))

      testKit.stop(store)
    }

    "再起動したときに保存した予約値が復元できる" in new Fixture {
      val nodeId        = 1
      val incrementStep = 3
      val store1        = spawnSequenceStore(nodeId, incrementStep)

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

      val store2 = spawnSequenceStore(nodeId, incrementStep)

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

    "継続不可能な例外によってセッション準備に失敗した後、次の採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      // SequenceStore は継続不可能な例外によってセッション準備に失敗する:
      failNextExecuteAsync(new RuntimeException("expected exception for test"))

      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      // SequenceStore は、再起動した後、次の採番予約を処理する:
      eventually {
        store ! SequenceStore.InitialReserveSequence(
          firstValue = 1,
          reservationAmount = 101,
          sequenceSubId,
          testProbe.ref,
        )
        testProbe.expectMessage(
          SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
        )
      }

      testKit.stop(store)
    }

    "継続可能な例外によってセッション準備に失敗した後、次の採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      // SequenceStore は継続可能な例外によって採番予約に失敗する:
      locally {
        val node = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        failNextExecuteAsync(new ReadTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, false))
      }

      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      // SequenceStore は、次の採番予約を処理する:
      eventually {
        store ! SequenceStore.InitialReserveSequence(
          firstValue = 1,
          reservationAmount = 101,
          sequenceSubId,
          testProbe.ref,
        )
        testProbe.expectMessage(
          SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
        )
      }

      testKit.stop(store)
    }

    "継続不可能な例外によって初期採番予約に失敗した後、次の初期採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      // SequenceStore が ready になっていることを確認するため:
      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続不可能な例外によって初期採番予約に失敗する:
      failNextExecuteAsync(new RuntimeException("expected exception for test"))
      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、再起動した後、次の初期採番予約を処理する:
      eventually {
        store ! SequenceStore.InitialReserveSequence(
          firstValue = 1,
          reservationAmount = 101,
          sequenceSubId,
          testProbe.ref,
        )
        testProbe.expectMessage(
          SequenceStore.InitialSequenceReserved(initialValue = 304, maxReservedValue = 604),
        )
      }

      testKit.stop(store)
    }

    "継続可能な例外によって初期採番予約に失敗した後、次の初期採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      // SequenceStore が ready になっていることを確認するため:
      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続可能な例外によって初期採番予約に失敗する:
      locally {
        val node = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        failNextExecuteAsync(new ReadTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, false))
      }
      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、次の初期採番予約を処理する:
      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 304, maxReservedValue = 604),
      )

      testKit.stop(store)
    }

    "継続不可能な例外によって採番予約に失敗した後、次の採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続不可能な例外によって採番予約に失敗する:
      failNextExecuteAsync(new RuntimeException("expected exception for test"))
      store ! SequenceStore.ReserveSequence(
        maxReservedValue = 301,
        reservationAmount = 100,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、再起動した後、次の採番予約を処理する:
      eventually {
        store ! SequenceStore.ReserveSequence(
          maxReservedValue = 301,
          reservationAmount = 100,
          sequenceSubId,
          testProbe.ref,
        )
        testProbe.expectMessage(SequenceStore.SequenceReserved(maxReservedValue = 601))
      }

      testKit.stop(store)
    }

    "継続可能な例外によって採番予約に失敗した後、次の採番予約を処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続可能な例外によって採番予約に失敗する:
      locally {
        val node = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        failNextExecuteAsync(new ReadTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, false))
      }
      store ! SequenceStore.ReserveSequence(
        maxReservedValue = 301,
        reservationAmount = 100,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、次の採番予約を処理する:
      store ! SequenceStore.ReserveSequence(
        maxReservedValue = 301,
        reservationAmount = 100,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.SequenceReserved(maxReservedValue = 601))

      testKit.stop(store)
    }

    "継続不可能な例外によって採番リセットに失敗した後、次の採番リセットを処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続不可能な例外によって採番リセットに失敗する:
      failNextExecuteAsync(new RuntimeException("expected exception for test"))
      store ! SequenceStore.ResetReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、再起動した後、次の採番リセットを処理する:
      eventually {
        store ! SequenceStore.ResetReserveSequence(
          firstValue = 1,
          reservationAmount = 101,
          sequenceSubId,
          testProbe.ref,
        )
        testProbe.expectMessage(SequenceStore.SequenceReset(maxReservedValue = 301))
      }

      testKit.stop(store)
    }

    "継続可能な例外によって採番リセットに失敗した後、次の採番リセットを処理する" in new Fixture with CqlStatementExecutorStubFixture {
      val store = spawnSequenceStore(nodeId = 1, incrementStep = 3, executor = executorStub)

      store ! SequenceStore.InitialReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(
        SequenceStore.InitialSequenceReserved(initialValue = 1, maxReservedValue = 301),
      )

      // SequenceStore は継続可能な例外によって採番リセットに失敗する:
      locally {
        val node = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        failNextExecuteAsync(new ReadTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, false))
      }
      store ! SequenceStore.ResetReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.ReservationFailed)

      // SequenceStore は、次の採番リセットを処理する:
      store ! SequenceStore.ResetReserveSequence(
        firstValue = 1,
        reservationAmount = 101,
        sequenceSubId,
        testProbe.ref,
      )
      testProbe.expectMessage(SequenceStore.SequenceReset(maxReservedValue = 301))

      testKit.stop(store)
    }

  }

  "SequenceStore.shouldRestartToRecoverFrom" should {
    import org.scalatest.prop.TableDrivenPropertyChecks._

    "再起動しなくてよい例外には false を返す" in {
      val exceptions = {
        val node      = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        val reasonMap = Map.empty[InetAddress, Integer].asJava
        Table(
          "exception",
          new UnavailableException(node, ConsistencyLevel.LOCAL_QUORUM, 2, 1),
          new ReadTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, false),
          new WriteTimeoutException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, WriteType.SIMPLE),
          new ReadFailureException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, 2, false, reasonMap),
          new WriteFailureException(node, ConsistencyLevel.LOCAL_QUORUM, 1, 2, WriteType.SIMPLE, 2, reasonMap),
        )
      }
      forAll(exceptions) { exception =>
        assert(!SequenceStore.shouldRestartToRecoverFrom(exception))
      }
    }

    "再起動すべき例外には true を返す" in {
      val exceptions = {
        val node    = session.execute("SELECT uuid() FROM system.local;").getExecutionInfo.getCoordinator
        val address = node.getListenAddress.get()
        val errors: List[util.Map.Entry[Node, Throwable]] = List(
          new util.AbstractMap.SimpleEntry(node, new RuntimeException("expected exception for test")),
        )
        Table(
          "exception",
          new ClosedConnectionException("expected exception for test"),
          new HeartbeatException(address, "expected exception for test", new RuntimeException()),
          new OverloadedException(node),
          new ServerError(node, "expected exception for test"),
          new TruncateException(node, "expected exception for test"),
          AllNodesFailedException.fromErrors(errors.asJava),
          new DriverTimeoutException("expected exception for test"),
        )
      }
      forAll(exceptions) { exception =>
        assert(SequenceStore.shouldRestartToRecoverFrom(exception))
      }
    }

  }

}
