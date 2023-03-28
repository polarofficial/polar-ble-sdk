package com.polar.androidcommunications.common.ble;

import androidx.annotation.NonNull;

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;

import java.util.Set;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.SingleEmitter;

public final class RxUtils {

    private RxUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> void postDisconnectedAndClearList(@NonNull AtomicSet<T> list) {
        postError(list, new BleDisconnected());
    }

    public static <T> void postExceptionAndClearList(@NonNull AtomicSet<T> list, @NonNull Throwable throwable) {
        postError(list, throwable);
    }

    public static <T> void postError(@NonNull AtomicSet<T> list, @NonNull Throwable throwable) {
        Set<T> objects = list.objects();
        for (T emitter : objects) {
            if (emitter instanceof ObservableEmitter) {
                if (!((ObservableEmitter) emitter).isDisposed()) {
                    ((ObservableEmitter) emitter).tryOnError(throwable);
                }
            } else if (emitter instanceof SingleEmitter) {
                if (!((SingleEmitter) emitter).isDisposed()) {
                    ((SingleEmitter) emitter).tryOnError(throwable);
                }
            } else if (emitter instanceof FlowableEmitter) {
                if (!((FlowableEmitter) emitter).isCancelled()) {
                    ((FlowableEmitter) emitter).tryOnError(throwable);
                }
            } else if (emitter instanceof CompletableEmitter) {
                if (!((CompletableEmitter) emitter).isDisposed()) {
                    ((CompletableEmitter) emitter).tryOnError(throwable);
                }
            } else {
                throw new AssertionError("emitter type not found");
            }
        }
        list.clear();
    }

    public interface Emitter<T> {
        void item(T object);
    }

    public static <T> void emitNext(AtomicSet<T> list, Emitter<T> emitter) {
        Set<T> objects = list.objects();
        for (T e : objects) {
            emitter.item(e);
        }
    }

    public static <T> void complete(@NonNull AtomicSet<T> list) {
        Set<T> objects = list.objects();
        for (T emitter : objects) {
            if (emitter instanceof ObservableEmitter) {
                if (!((ObservableEmitter) emitter).isDisposed()) {
                    ((ObservableEmitter) emitter).onComplete();
                }
            } else if (emitter instanceof FlowableEmitter) {
                if (!((FlowableEmitter) emitter).isCancelled()) {
                    ((FlowableEmitter) emitter).onComplete();
                }
            } else if (emitter instanceof CompletableEmitter) {
                if (!((CompletableEmitter) emitter).isDisposed()) {
                    ((CompletableEmitter) emitter).onComplete();
                }
            } else {
                throw new AssertionError("emitter type not found");
            }
        }
        list.clear();
    }

    /**
     * Template type monitor notifications to remove broiler plate code
     *
     * @param observers       AtomicSet of observers
     * @param transport       for connection check
     * @param checkConnection optional check initial connection
     * @param <E>             type
     * @return Flowable stream
     */
    public static <E> Flowable<E> monitorNotifications(final AtomicSet<FlowableEmitter<? super E>> observers,
                                                       final BleGattTxInterface transport,
                                                       final boolean checkConnection) {
        final FlowableEmitter<? super E>[] listener = new FlowableEmitter[1];
        return Flowable.create((FlowableOnSubscribe<E>) subscriber -> {
                    if (!checkConnection || transport.isConnected()) {
                        listener[0] = subscriber;
                        observers.add(subscriber);
                    } else {
                        subscriber.tryOnError(new BleDisconnected());
                    }
                }, BackpressureStrategy.BUFFER)
                .doFinally(() -> observers.remove(listener[0]))
                .serialize();
    }
}
