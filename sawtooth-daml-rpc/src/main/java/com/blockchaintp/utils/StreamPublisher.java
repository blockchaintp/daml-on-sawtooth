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

public class StreamPublisher<T> implements Publisher<T> {

	private final Supplier<Stream<? extends T>> streamSupplier;

	public StreamPublisher(Supplier<Stream<? extends T>> streamSupplier) {
		this.streamSupplier = streamSupplier;
	}

	@Override
	public void subscribe(Subscriber<? super T> subscriber) {
		StreamSubscription subscription = new StreamSubscription(subscriber);
		subscriber.onSubscribe(subscription);
		subscription.doOnSubscribed();
	}

	private class StreamSubscription implements Subscription {

		private Subscriber<? super T> subscriber;
		private final Iterator<? extends T> iterator;
		private final AtomicReference<Throwable> error = new AtomicReference<>();
		private final AtomicBoolean isTerminated = new AtomicBoolean(false);
		private final AtomicLong demand = new AtomicLong();
        
		public StreamSubscription(Subscriber<? super T> subscriber) {
			this.subscriber = subscriber;

			Iterator<? extends T> iterator = null;

			try {
				iterator = streamSupplier.get().iterator();
			} catch (Throwable e) {
				error.set(e);
			}

			this.iterator = iterator;
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
        public void request(long n) {
            if (n <= 0 && !terminate()) {
                subscriber.onError(new IllegalArgumentException("negative subscription request"));
                return;
            }

            for (; ; ) {
                long currentDemand = demand.getAcquire();

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
