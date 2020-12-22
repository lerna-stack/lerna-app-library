package lerna.util.sequence

import akka.actor.{ ActorSystem, PoisonPill }
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import lerna.util.tenant.Tenant

import scala.concurrent.duration._

object SequenceFactoryWorkerSpec {

  private val config = ConfigFactory.parseString("""
    | akka.actor {
    |   provider = local
    | }
    """.stripMargin)
}

class SequenceFactoryWorkerSpec
    extends LernaSequenceActorBaseSpec(ActorSystem("SequenceFactoryWorkerSpec", SequenceFactoryWorkerSpec.config)) {

  private implicit val tenant: Tenant = new Tenant {
    override def id: String = "dummy"
  }

  val sequenceSubId: Option[String] = Option("test")

  "SequenceFactoryWorker" should {

    "採番の初項は firstValue で指定できる" in {
      val storeProbe = TestProbe()
      val worker = system.actorOf(
        SequenceFactoryWorker
          .props(
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 11,
            storeProbe.testActor,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )
      // reservationAmount の個数だけ採番できるように予約
      storeProbe.expectMsg(SequenceStore.InitialReserveSequence(firstValue = 3, reservationAmount = 11, sequenceSubId))
      storeProbe.reply(SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 113))

      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId)
      expectMsg(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))

      worker ! PoisonPill
    }

    "予約した採番値が枯渇した場合は新たに予約して採番する" in {
      val storeProbe = TestProbe()
      val worker = system.actorOf(
        SequenceFactoryWorker
          .props(
            maxSequenceValue = 999,
            firstValue = 3,
            incrementStep = 10,
            reservationAmount = 1,
            storeProbe.testActor,
            idleTimeout = 10.seconds,
            sequenceSubId,
          ),
      )
      storeProbe.expectMsg(SequenceStore.InitialReserveSequence(firstValue = 3, reservationAmount = 1, sequenceSubId))
      storeProbe.reply(SequenceStore.InitialSequenceReserved(initialValue = 3, maxReservedValue = 3))

      // reservationAmount が 1 なので、1回採番すると枯渇
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId)
      expectMsg(SequenceFactoryWorker.SequenceGenerated(3, sequenceSubId))

      // 枯渇した場合は新たな採番値を予約して採番
      worker ! SequenceFactoryWorker.GenerateSequence(sequenceSubId)
      storeProbe.expectMsg(SequenceStore.ReserveSequence(maxReservedValue = 3, reservationAmount = 1, sequenceSubId))
      storeProbe.reply(SequenceStore.SequenceReserved(maxReservedValue = 13))
      expectMsg(SequenceFactoryWorker.SequenceGenerated(13, sequenceSubId))

      worker ! PoisonPill
    }
  }

}
