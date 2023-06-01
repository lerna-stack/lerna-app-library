package lerna.util.sequence

import akka.actor.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant

import java.util.UUID
import scala.concurrent.Future

object CassandraSequenceFactorySpec {

  class TestSequenceFactory(
      val seqId: String,
      val maxSequence: BigInt,
      val sequenceCacheSize: Int,
      val supportedTenants: Seq[Tenant] = Seq(tenant),
  )(implicit val system: ActorSystem, val config: Config)
      extends CassandraSequenceFactory

  private implicit val tenant: Tenant = new Tenant {
    override def id: String = "dummy"
  }

  private val baseConfig: Config = ConfigFactory
    .parseString(
      s"""
                                                               | akka.actor {
                                                               |   provider = local
                                                               | }
                                                               | akka.test.default-timeout = 10s
                                                               | lerna.util.sequence {
                                                               |   node-id = 1
                                                               |   max-node-id = 9
                                                               |   cassandra.tenants.${tenant.id} = $${lerna.util.sequence.cassandra.default}
                                                               | }
                                                 """.stripMargin,
    ).withFallback(ConfigFactory.defaultReferenceUnresolved()).resolve()

}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.contrib.warts.MissingOverride",
  ),
)
class CassandraSequenceFactorySpec
    extends LernaSequenceActorBaseSpec(
      ActorSystem("CassandraSequenceFactorySpec", CassandraSequenceFactorySpec.baseConfig),
    ) {

  import CassandraSequenceFactorySpec._

  private[this] lazy val cassandraConfig = new SequenceFactoryConfig(baseConfig).cassandraConfig

  private lazy val session =
    CqlSessionProvider.connect(system, cassandraConfig).futureValue

  override def beforeAll(): Unit = {
    super.beforeAll()
    SequenceTestkit.dropKeyspaceIfExists(session, cassandraConfig)
  }

  override def afterAll(): Unit = {
    try session.close()
    finally super.afterAll()
  }

  import system.dispatcher

  "SequenceFactory" should {

    "初項は node-id で、公差が max-node-id のシーケンス番号が発行される" in {

      implicit val config: Config = baseConfig

      val sequenceFactory: SequenceFactory =
        new TestSequenceFactory(seqId = UUID.randomUUID().toString, maxSequence = 9999999, sequenceCacheSize = 11)

      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(1)) // 初項は node-id
      }
      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(10)) // 公差が max-node-id
      }
      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(19)) // 公差が max-node-id
      }
      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(28)) // 公差が max-node-id
      }
      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(37)) // 公差が max-node-id
      }
    }

    "sequenceId が同じで sequenceSubId が異なる採番要求が連続で行われても採番できる" in {

      implicit val config: Config = baseConfig

      val sequenceFactory: SequenceFactory =
        new TestSequenceFactory(seqId = UUID.randomUUID().toString, maxSequence = 1000, sequenceCacheSize = 11)

      val numberOfValues = 100

      val futures = Future.sequence(for {
        i <- 1 to numberOfValues
      } yield {
        val sequenceSubId = s"test-${i.toString}"
        sequenceFactory.nextId(sequenceSubId)
      })

      whenReady(futures) { results =>
        // 初項は node-id
        expect(results.forall(_ === BigInt(1)))
        // 全部 採番成功して値がある(そもそもFutureがSuccessなら問題ない)
        expect(results.size === numberOfValues)
      }
    }

    "maxSequenceValue を超えるシーケンスが発行されると初項にリセットされる" in {

      implicit val config: Config = baseConfig

      val sequenceFactory: SequenceFactory =
        new TestSequenceFactory(seqId = UUID.randomUUID().toString, maxSequence = 1000, sequenceCacheSize = 11)

      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(1)) // 初項は node-id
      }

      val numberOfValues = 110

      val futures = Future.sequence(for {
        _ <- 1 to numberOfValues
      } yield {
        Thread.sleep(10) // 警告が出過ぎるのを抑止
        sequenceFactory.nextId()
      })

      whenReady(futures) { results =>
        expect {
          results.lastOption === Option(BigInt(991)) // 1 + 9 * 110
        }
      }

      whenReady(sequenceFactory.nextId()) { sequence =>
        expect(sequence === BigInt(1000)) // maxSequence
      }

      whenReady(sequenceFactory.nextId()) { sequence =>
        // maxSequence を超えたら リセットされて初項になる
        expect(sequence === BigInt(1))
      }
    }

    "maxSequenceValueが非常に大きい値の場合に`freeAmount`の計算結果がオーバーフローを発生しない" in {
      implicit val config: Config = baseConfig

      val initialValue      = config.getInt("lerna.util.sequence.node-id")
      val incrementStep     = config.getInt("lerna.util.sequence.max-node-id")
      val sequenceCacheSize = 11
      val maxReservedValue  = initialValue + (incrementStep * (sequenceCacheSize - 1))
      // freeAmountの計算で利用する `最大シーケンス番号までの間で採番可能なシーケンス数` がLong型の最大値を超えるようにmaxSequenceを設定
      val maxSequence =
        (BigInt(Long.MaxValue) + 1) * config.getInt("lerna.util.sequence.max-node-id") + maxReservedValue

      val sequenceFactory: SequenceFactory = new TestSequenceFactory(
        seqId = UUID.randomUUID().toString,
        maxSequence,
        sequenceCacheSize,
      )

      // maxReservedValue が更新されるまで採番要求をおこなう
      val sequence: Future[List[BigInt]] = Future.traverse((1 to 12).toList) { _ => sequenceFactory.nextId() }

      whenReady(sequence) { seq =>
        expect(seq === List(1, 10, 19, 28, 37, 46, 55, 64, 73, 82, 91, 100).map(BigInt(_)))
      }
    }

    "対応テナントではない場合Future.failedになる" in {

      implicit val config: Config = baseConfig

      val sequenceFactory: SequenceFactory =
        new TestSequenceFactory(
          seqId = UUID.randomUUID().toString,
          maxSequence = 1000,
          sequenceCacheSize = 11,
          supportedTenants = Seq(), // !!! empty
        )
      whenReady(sequenceFactory.nextId().failed) { throwable =>
        throwable shouldBe a[IllegalArgumentException]
        expect(throwable.getMessage === "tenant(dummy) must be included in supportedTenants")
      }
    }

  }

}

object CassandraSequenceFactoryTypedSpec {
  class TestSequenceFactory(
      val system: akka.actor.typed.ActorSystem[Nothing],
      val config: Config,
      val seqId: String = UUID.randomUUID().toString,
      val maxSequence: BigInt = 9999999,
      val sequenceCacheSize: Int = 11,
      val supportedTenants: Seq[Tenant] = Seq(tenant),
  ) extends CassandraSequenceFactory

  private implicit val tenant: Tenant = new Tenant {
    override def id: String = "dummy"
  }

  implicit private val config: Config = ConfigFactory
    .parseString(s"""
         | akka.actor {
         |   provider = local
         | }
         | akka.test.default-timeout = 10s
         | lerna.util.sequence {
         |   node-id = 1
         |   max-node-id = 9
         |   cassandra.tenants.${tenant.id} = $${lerna.util.sequence.cassandra.default}
         | }
         | """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved()).resolve()

}

class CassandraSequenceFactoryTypedSpec
    extends ScalaTestWithTypedActorTestKit(CassandraSequenceFactoryTypedSpec.config)
    with LernaBaseSpec {
  import CassandraSequenceFactoryTypedSpec._

  "CassandraSequenceFactory" can {
    "be created using typed ActorSystem" in {
      val typedSystem: akka.actor.typed.ActorSystem[Nothing] = system
      new TestSequenceFactory(typedSystem, config) // 例外が throw されずインスタンス生成に成功する
    }
  }
}
