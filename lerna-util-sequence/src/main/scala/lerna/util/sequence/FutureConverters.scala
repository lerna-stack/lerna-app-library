/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
// 移行作業のため一時的にコピーして使用している。
// TODO 移行作業のため一時的にコピーしているので、このクラスを使わないように書き換えていくこと

package lerna.util.sequence

import com.google.common.util.concurrent.ListenableFuture

import java.util.concurrent.Executor
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

object FutureConverters {
  // Copied from
  // https://github.com/akka/akka-persistence-cassandra/blob/v0.98/core/src/main/scala/akka/persistence/cassandra/package.scala#L46-L54
  @SuppressWarnings(
    // もともとのコードをなるべく書き換えたくないので、エラーが出たlintを無効にする。
    // ただし、Runable で発生した MissingOverride だけは簡単な対処なので対応した。
    Array(
      "org.wartremover.warts.AsInstanceOf",
    ),
  )
  private[sequence] implicit class ListenableFutureConverter[A](val lf: ListenableFuture[A]) extends AnyVal {
    def asScala(implicit ec: ExecutionContext): Future[A] = {
      val promise = Promise[A]()
      lf.addListener(
        new Runnable {
          override def run() = promise.complete(Try(lf.get()))
        },
        ec.asInstanceOf[Executor],
      )
      promise.future
    }
  }
}
