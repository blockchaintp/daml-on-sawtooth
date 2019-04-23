package com.blockchaintp.utils;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A Publisher for a generic Stream.
 * @author scealiontach
 * @param <T>
 */
public final class StreamPublisher<T> implements Publisher<T> {

  private final Supplier<Stream<? extends T>> streamSupplier;

  /**
   * Given a supplier construct a StreamPublisher.
   * @param argStreamSupplier a supplier of Streams
   */
  public StreamPublisher(final Supplier<Stream<? extends T>> argStreamSupplier) {
    this.streamSupplier = argStreamSupplier;
  }

  @Override
  public void subscribe(final Subscriber<? super T> argSubscriber) {
    StreamSubscription subscription = new StreamSubscription(argSubscriber);
    argSubscriber.onSubscribe(subscription);
    subscription.doOnSubscribed();
  }

  /**
   * A Subscription to a StreamPublisher.
   * @author scealiontach
   *
   */
  private class StreamSubscription implements Subscription {

    private Subscriber<? super T> subscriber;
    private final Iterator<? extends T> iterator;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean isTerminated = new AtomicBoolean(false);
    private final AtomicLong demand = new AtomicLong();

    StreamSubscription(final Subscriber<? super T> argSubscriber) {
      this.subscriber = argSubscriber;

      Iterator<? extends T> tmpIterator = null;

      try {
        tmpIterator = streamSupplier.get().iterator();
      } catch (Throwable e) {
        error.set(e);
      }

      this.iterator = tmpIterator;
    }

    void doOnSubscribed() {
      Throwable throwable = error.get();
      if (throwable != null && !terminate()) {
        subscriber.onError(throwable);
      }
    }

    private boolean terminate() {
      return isTerminated.getAndSet(true);
    }

    private boolean isTerminated() {
      return isTerminated.get();
    }

    @Override
    public void request(final long n) {
      if (n <= 0 && !terminate()) {
        subscriber.onError(new IllegalArgumentException("negative subscription request"));
        return;
      }

      for (;;) {
        long currentDemand = demand.get();

        if (currentDemand == Long.MAX_VALUE) {
          return;
        }

        long adjustedDemand = currentDemand + n;

        if (adjustedDemand < 0L) {
          adjustedDemand = Long.MAX_VALUE;
        }

        if (demand.compareAndSet(currentDemand, adjustedDemand)) {
          if (currentDemand > 0) {
            return;
          }

          break;
        }
      }

      for (; demand.get() > 0 && iterator.hasNext() && !isTerminated(); demand.decrementAndGet()) {
        try {
          subscriber.onNext(iterator.next());
        } catch (Throwable e) {
          if (!terminate()) {
            subscriber.onError(e);
          }
        }
      }

      if (!iterator.hasNext() && !terminate()) {
        subscriber.onComplete();
      }
    }

    @Override
    public void cancel() {
      terminate();
    }

  }

}
