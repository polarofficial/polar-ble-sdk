package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class ChannelUtils private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        fun <T : Any> postDisconnectedAndClearList(listSingle: AtomicSet<Channel<T>>) {
            postError(listSingle, BleDisconnected())
        }

        fun <T : Any> postExceptionAndClearList(listFlowable: AtomicSet<Channel<T>>, throwable: Throwable) {
            postError(listFlowable, throwable)
        }

        fun <T : Any> postError(list: AtomicSet<Channel<T>>, throwable: Throwable) {
            if (throwable != null) {
                val objects = list.objects()
                for (channel in objects) {
                    val cancellationEx = CancellationException("Channel closed due to error", throwable)
                    channel.cancel(cancellationEx)
                }
            }
            list.clear()
        }

        fun <T : Any> updateView(obj: T) {
            // Access the objects here to update the view
        }

        fun <T : Any> emitNext(list: AtomicSet<T>, emitter: (T) -> Unit) {
            val objects = list.objects()
            for (e: T in objects) {
                emitter(e)
            }
        }

        fun <T : Any> complete(list: AtomicSet<Channel<T>>) {
            val objects = list.objects()
            for (channel in objects) {
                channel.close()
            }
            list.clear()
        }

        /**
         * Template type monitor notifications to remove boilerplate code
         *
         * @param observers       AtomicSet of observers
         * @param transport       for connection check
         * @param checkConnection optional check initial connection
         * @return Flow stream
         */
        fun <T : Any> monitorNotifications(
            observers: AtomicSet<Channel<T>>,
            transport: BleGattTxInterface,
            checkConnection: Boolean
        ): Flow<T> {
            return callbackFlow {
                if (!checkConnection || transport.isConnected()) {
                    val observer = Channel<T>(Channel.BUFFERED)
                    observers.add(observer)

                    // Bridge observer channel values to the flow collector channel.
                    // When the observer channel is closed normally (e.g. all chars received),
                    // also close the callbackFlow producer so the flow completes downstream.
                    val bridgeJob = launch {
                        try {
                            for (item in observer) {
                                send(item)
                            }
                            // observer was closed normally — complete the flow
                            close()
                        } catch (e: Throwable) {
                            // observer was closed with an error — forward the original cause
                            close(e)
                        }
                    }

                    awaitClose {
                        observers.remove(observer)
                        observer.close()
                        bridgeJob.cancel()
                    }
                } else {
                    close(BleDisconnected())
                }
            }
        }
    }
}
