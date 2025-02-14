package com.polar.androidcommunications.api.ble.model.gatt.client.psftp;


import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

import com.polar.androidcommunications.api.ble.BleLogger;
import com.polar.androidcommunications.api.ble.exceptions.BleAttributeError;
import com.polar.androidcommunications.api.ble.exceptions.BleCharacteristicNotificationNotEnabled;
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase;
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.polar.androidcommunications.api.ble.model.proto.CommunicationsPftpRequest;

/**
 * Polar simple file transfer client declaration.
 */
public class BlePsFtpClient extends BleGattBase {

    private static final String TAG = "BlePsFtpClient";
    private final AtomicInteger pftpMtuEnabled;
    private final AtomicInteger pftpD2HNotificationEnabled;
    private final LinkedBlockingQueue<Pair<byte[], Integer>> mtuInputQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Pair<byte[], Integer>> notificationInputQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger packetsWritten = new AtomicInteger(0);
    private final AtomicInteger packetsWrittenWithResponse = new AtomicInteger(0);
    private final AtomicBoolean mtuWaiting = new AtomicBoolean(false);
    private final AtomicBoolean currentOperationWrite = new AtomicBoolean(false);
    private final AtomicBoolean notificationWaiting = new AtomicBoolean(false);
    private final AtomicInteger notificationPacketsWritten = new AtomicInteger(0);
    private final AtomicInteger packetsCount = new AtomicInteger(5); // default every 5th packet is written with response
    private static final int PROTOCOL_TIMEOUT_SECONDS = 90;
    private static final int PROTOCOL_TIMEOUT_EXTENDED_SECONDS = 900;

    private final List<String> extendedWriteTimeoutFilePaths = Collections.singletonList("/SYNCPART.TGZ");

    /**
     * true  = uses attribute operation WRITE
     * false = uses attribute operation WRITE_NO_RESPONSE
     */
    private final AtomicBoolean useAttributeLevelResponse = new AtomicBoolean(false);
    private final Object pftpOperationMutex = new Object();
    private final Object pftpNotificationMutex = new Object();
    private final Object pftpWaitNotificationMutex = new Object();
    private final Object pftpWaitNotificationSharedMutex = new Object();
    private Flowable<BlePsFtpUtils.PftpNotificationMessage> _sharedWaitNotificationFlowable = null;

    public BlePsFtpClient(BleGattTxInterface txInterface) {
        super(txInterface, BlePsFtpUtils.RFC77_PFTP_SERVICE, true);
        addCharacteristicNotification(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC);
        addCharacteristicNotification(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC);
        addCharacteristic(BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC);
        pftpMtuEnabled = getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC);
        pftpD2HNotificationEnabled = getNotificationAtomicInteger(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC);
        setIsPrimaryService(true);
    }

    /**
     * set amount of packets written consecutive with BLE ATT WRITE before adding the BLE ATT WRITE REQUEST
     * <p>
     * BLE ATT WRITE is the write which don't wait response from the GATT server as BLE ATT WRITE
     * REQUEST waits that GATT server responses before next write is attempt. With BLE ATT WRITE
     * REQUEST server can control if its getting too busy to handle all the write events by delaying the response.
     * So it is good send BLE ATT WRITE REQUEST every now and then.
     *
     * @param count number of packets
     */
    public void setPacketsCount(int count) {
        packetsCount.set(count);
    }

    public int getPacketsCount() {
        return packetsCount.get();
    }

    @Override
    public void reset() {
        super.reset();
        currentOperationWrite.set(false);
        mtuInputQueue.clear();
        synchronized (mtuInputQueue) {
            mtuInputQueue.notifyAll();
        }

        packetsWritten.set(0);
        synchronized (packetsWritten) {
            packetsWritten.notifyAll();
        }

        packetsWrittenWithResponse.set(0);
        synchronized (packetsWrittenWithResponse) {
            packetsWrittenWithResponse.notifyAll();
        }

        notificationInputQueue.clear();
        synchronized (notificationInputQueue) {
            notificationInputQueue.notifyAll();
        }

        notificationPacketsWritten.set(0);
        synchronized (notificationPacketsWritten) {
            notificationPacketsWritten.notifyAll();
        }

        mtuWaiting.set(false);
        notificationWaiting.set(false);
    }

    @Override
    public void processServiceData(UUID characteristic, byte[] data, int status, boolean notifying) {
        if (data.length != 0) {
            if (characteristic.equals(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)) {
                synchronized (mtuInputQueue) {
                    mtuInputQueue.add(new Pair<>(data, status));
                    mtuInputQueue.notifyAll();
                }
                if (currentOperationWrite.get() && mtuWaiting.get() && data.length == 3) {
                    // special case stream cancellation has been received before att response
                    synchronized (packetsWritten) {
                        packetsWritten.incrementAndGet();
                        packetsWritten.notifyAll();
                    }
                }
            } else if (characteristic.equals(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC)) {
                synchronized (notificationInputQueue) {
                    notificationInputQueue.add(new Pair<>(data, status));
                    notificationInputQueue.notifyAll();
                }
            }
        } else {
            BleLogger.e(TAG, "Received 0 length packet");
        }
    }

    @Override
    public void processServiceDataWritten(UUID characteristic, int status) {
        if (status == 0) {
            if (characteristic.equals(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)) {
                synchronized (packetsWritten) {
                    packetsWritten.incrementAndGet();
                    packetsWritten.notifyAll();
                }
            } else if (characteristic.equals(BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC)) {
                synchronized (notificationPacketsWritten) {
                    notificationPacketsWritten.incrementAndGet();
                    notificationPacketsWritten.notifyAll();
                }
            }
        } else {
            // print informal info, NOTE current implementation will result to timeout, which is ok as both ends will
            // reset them selfs with timeout
            BleLogger.e(TAG, "Failed to write chr UUID: " + characteristic.toString() + " status: " + status);
        }
    }

    @Override
    public void processServiceDataWrittenWithResponse(UUID characteristic, int status) {
        if (status == 0) {
            if (characteristic.equals(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC)) {
                synchronized (packetsWrittenWithResponse) {
                    packetsWrittenWithResponse.incrementAndGet();
                    packetsWrittenWithResponse.notifyAll();
                }
            }
        }
        processServiceDataWritten(characteristic, status);
    }

    @Override
    public @NonNull
    String toString() {
        return "RFC77 Service";
    }

    private void resetMtuPipe() {
        BleLogger.d(TAG, "mtu reseted");
        mtuInputQueue.clear();
        packetsWritten.set(0);
        mtuWaiting.set(false);
    }

    private void resetNotificationPipe() {
        notificationPacketsWritten.set(0);
        notificationWaiting.set(false);
    }

    @Override
    public Completable clientReady(boolean checkConnection) {
        return waitPsFtpClientReady(checkConnection);
    }

    public Single<ByteArrayOutputStream> request(final byte[] header) {
        return request(header, Schedulers.newThread());
    }

    /**
     * Sends a request to device (get, remove or put(create dir, without data)), atomic operation<BR>
     *
     * @param header    protobuf pftp operation bytes, GET , REMOVE or PUT(create dir etc... without data) <BR>
     * @param scheduler scheduler where to start operation execution<BR>
     * @return Observable Produces: onNext, only once when file has been successfully read<BR>
     * onCompleted, called after onNext<BR>
     * onError, if any error happens during file operation, @see BlePsFtpUtils for possible exceptions <BR>
     */
    public Single<ByteArrayOutputStream> request(final byte[] header, Scheduler scheduler) {
        return Single.create((SingleOnSubscribe<ByteArrayOutputStream>) emitter -> {
                    // block, until previous operation has completed
                    try {
                        synchronized (pftpOperationMutex) {
                            if (pftpMtuEnabled.get() == ATT_SUCCESS) {
                                BleLogger.d(TAG, "Start request");
                                resetMtuPipe();
                                // transmit header first
                                ByteArrayInputStream totalStream = BlePsFtpUtils.makeCompleteMessageStream(new ByteArrayInputStream(header), null, BlePsFtpUtils.MessageType.REQUEST, 0);
                                BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
                                List<byte[]> requestData = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber);
                                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                try {
                                    txInterface.transmitMessages(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, requestData, false);
                                    waitPacketsWritten(packetsWritten, mtuWaiting, requestData.size(), PROTOCOL_TIMEOUT_SECONDS);
                                    requestData.clear();
                                    // start waiting for packets
                                    readResponse(outputStream, PROTOCOL_TIMEOUT_SECONDS);
                                    emitter.onSuccess(outputStream);
                                } catch (InterruptedException ex) {
                                    BleLogger.e(TAG, "Request interrupted. Exception: " + ex.getMessage());
                                    // canceled, note make improvement, only wait amount of packets transmitted
                                    handleMtuInterrupted(true, requestData.size());
                                }
                            } else {
                                BleLogger.e(TAG, "Request failed. PS-FTP MTU not enabled");
                                throw new BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled");
                            }
                        }
                    } catch (Exception ex) {
                        BleLogger.e(TAG, "Request failed. Exception: " + ex.getMessage());
                        if (!emitter.isDisposed()) {
                            emitter.tryOnError(ex);
                        }
                    }
                })
                .doOnSubscribe(disposable -> txInterface.gattClientRequestStopScanning())
                .doFinally(txInterface::gattClientResumeScanning)
                .subscribeOn(scheduler);
    }

    private void waitPacketsWritten(final AtomicInteger written, AtomicBoolean waiting, int count, long timeoutSeconds) throws InterruptedException, BleDisconnected, BlePsFtpUtils.PftpOperationTimeout {
        try {
            waiting.set(true);
            while (written.get() < count) {
                synchronized (written) {
                    if (written.get() != count) {
                        int was = written.get();
                        written.wait(timeoutSeconds * 1000);
                        if (was == written.get()) {
                            if (!txInterface.isConnected()) {
                                throw new BleDisconnected("Connection lost during waiting packets to be written");
                            } else {
                                throw new BlePsFtpUtils.PftpOperationTimeout("Operation timeout while waiting packets written");
                            }
                        }
                    }
                }
                if (!txInterface.isConnected()) {
                    throw new BleDisconnected("Connection lost during waiting packets to be written");
                }
            }
        } finally {
            waiting.set(false);
            written.set(0);
        }
    }

    public Flowable<Long> write(final byte[] header, final ByteArrayInputStream data) {
        return write(header, data, Schedulers.newThread());
    }

    /**
     * Writes file to a device, atomic operation<BR>
     *
     * @param header protobuf pftp operation bytes<BR>
     * @param data   actual file data<BR>
     * @return Observable stream of byte offset progress<BR>
     * Produces: onNext current count of bytes written to device<BR>
     * onError any error during file transfer, @see BlePsFtpUtils for possible exceptions<BR>
     * onCompleted after file has been successfully been written to device, and device has<BR>
     * replyed ok<BR>
     */
    public Flowable<Long> write(final byte[] header, final ByteArrayInputStream data, final Scheduler scheduler) {
        return Flowable.create((FlowableOnSubscribe<Long>) subscriber -> {
                    // block until previous operation has completed
                    synchronized (pftpOperationMutex) {
                        if (pftpMtuEnabled.get() == ATT_SUCCESS) {
                            BleLogger.d(TAG, "Start write");

                            currentOperationWrite.set(true);
                            long pCounter = 0;
                            resetMtuPipe();
                            // clear all before new operation
                            ByteArrayInputStream totalStream;
                            int headerSize = header.length;
                            totalStream = BlePsFtpUtils.makeCompleteMessageStream(new ByteArrayInputStream(header), data, BlePsFtpUtils.MessageType.REQUEST, 0);
                            int next = 0;
                            long totalPayload = totalStream.available();
                            BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
                            final long timeoutSeconds = getWriteTimeoutForFilePath(CommunicationsPftpRequest.PbPFtpOperation.parseFrom(header).getPath());
                            do {
                                byte[] airPacket;
                                int counter = 0;
                                try {
                                    int temp = next; // workaround for stupid java translator idiotisim
                                    airPacket = BlePsFtpUtils.buildRfc76MessageFrame(totalStream, temp, mtuSize.get(), sequenceNumber);
                                    next = 1;
                                    // transmit one frame
                                    useAttributeLevelResponse.set((++pCounter % (packetsCount.get())) == 0);
                                    txInterface.transmitMessage(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, airPacket, useAttributeLevelResponse.get());
                                    if (totalStream.available() != 0) {
                                        if (useAttributeLevelResponse.get()) {
                                            counter = 1;
                                            packetsWrittenWithResponse.set(0);
                                            waitPacketsWritten(packetsWrittenWithResponse, mtuWaiting, 1, timeoutSeconds);
                                            packetsWritten.set(0);
                                            counter = 0;
                                        } else {
                                            ++counter;
                                        }
                                        // check input stream, give some time to respond
                                        Pair<byte[], Integer> packet = mtuInputQueue.poll();
                                        if (packet != null && packet.second == 0) {
                                            BleLogger.e(TAG, "Frame sending interrupted by device!");
                                            BlePsFtpUtils.PftpRfc76ResponseHeader response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first);
                                            if (response.status == 0) {
                                                if (!subscriber.isCancelled()) {
                                                    subscriber.tryOnError(new BlePsFtpUtils.PftpResponseError("Stream canceled: ", response.error));
                                                }
                                            } else {
                                                if (!subscriber.isCancelled()) {
                                                    subscriber.tryOnError(new Throwable("Stream canceled"));
                                                }
                                            }
                                            return;
                                        }
                                    }
                                    long transferred = totalPayload - totalStream.available() - headerSize - 2;
                                    subscriber.onNext(transferred);
                                } catch (InterruptedException ex) {
                                    // Note RX throws InterruptedException when the stream is not completed, and it is unsubscribed
                                    // canceled
                                    BleLogger.e(TAG, "Frame sending interrupted!");
                                    handleMtuInterrupted(totalStream.available() != 0, counter);
                                    return;
                                } catch (Throwable throwable) {
                                    if (!subscriber.isCancelled()) {
                                        subscriber.tryOnError(throwable);
                                    }
                                    return;
                                }
                            } while (totalStream.available() != 0);

                            // receive response
                            currentOperationWrite.set(false);
                            ByteArrayOutputStream response = new ByteArrayOutputStream();
                            try {
                                readResponse(response, timeoutSeconds);
                            } catch (InterruptedException ex) {
                                // catch interrupted as it cannot be rethrown onwards
                                BleLogger.e(TAG, "write interrupted while reading response");
                                return;
                            } catch (Throwable throwable) {
                                if (!subscriber.isCancelled()) {
                                    subscriber.tryOnError(throwable);
                                }
                                return;
                            }
                            subscriber.onComplete();
                        } else if (!subscriber.isCancelled()) {
                            throw new BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled");
                        }
                    }
                }, BackpressureStrategy.LATEST)
                .doOnSubscribe(subscription -> txInterface.gattClientRequestStopScanning())
                .doFinally(() -> {
                    txInterface.gattClientResumeScanning();
                    currentOperationWrite.set(false);
                })
                .subscribeOn(scheduler)
                .serialize();
    }

    private long getWriteTimeoutForFilePath(final String filePath) {
        for (final String path : extendedWriteTimeoutFilePaths) {
            if (filePath.startsWith(path)) {
                return PROTOCOL_TIMEOUT_EXTENDED_SECONDS;
            }
        }
        return PROTOCOL_TIMEOUT_SECONDS;
    }

    public Single<ByteArrayOutputStream> query(final int id, final byte[] parameters) {
        return query(id, parameters, Schedulers.newThread());
    }

    private void handleMtuInterrupted(boolean dataAvailable, int lastRequest) {
        if (pftpMtuEnabled.get() == ATT_SUCCESS && dataAvailable) {
            //wait for packets written
            byte[] cancelPacket = new byte[]{0x00, 0x00, 0x00};
            try {
                if (mtuWaiting.get()) {
                    waitPacketsWritten(packetsWritten, mtuWaiting, lastRequest, PROTOCOL_TIMEOUT_SECONDS);
                }
                txInterface.transmitMessages(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, Collections.singletonList(cancelPacket), useAttributeLevelResponse.get());
                waitPacketsWritten(packetsWritten, mtuWaiting, 1, PROTOCOL_TIMEOUT_SECONDS);
                BleLogger.d(TAG, "MTU interrupted. Stream cancel has been successfully send");
            } catch (Throwable throwable) {
                BleLogger.e(TAG, "Exception while trying to cancel streaming");
            }
        }
    }

    /**
     * Sends a query to devicef
     *
     * @param id         query id value
     * @param parameters optional parameters
     * @return Observable stream of response data <BR>
     * Produces: onNext, successful query response with or without data, depending on query type <BR>
     * onError, @see BlePsFtpUtils for possible exceptions <BR>
     * onCompleted, after successfully completed query <BR>
     */
    public Single<ByteArrayOutputStream> query(final int id, final byte[] parameters, Scheduler scheduler) {
        return Single.create((SingleOnSubscribe<ByteArrayOutputStream>) emitter -> {
            // block until previous operation has completed
            try {
                synchronized (pftpOperationMutex) {
                    if (pftpMtuEnabled.get() == ATT_SUCCESS) {
                        BleLogger.d(TAG, "Send query id: " + id);
                        resetMtuPipe();
                        ByteArrayInputStream totalStream = BlePsFtpUtils.makeCompleteMessageStream(parameters != null ? new ByteArrayInputStream(parameters) : null, null, BlePsFtpUtils.MessageType.QUERY, id);
                        BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
                        List<byte[]> requs = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber);
                        ByteArrayOutputStream response = new ByteArrayOutputStream();
                        try {
                            txInterface.transmitMessages(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, requs, false);
                            waitPacketsWritten(packetsWritten, mtuWaiting, requs.size(), PROTOCOL_TIMEOUT_SECONDS);
                            requs.clear();
                            readResponse(response, PROTOCOL_TIMEOUT_SECONDS);
                            emitter.onSuccess(response);
                        } catch (InterruptedException ex) {
                            // Note RX throws InterruptedException when the stream is not completed, and it is unsubscribed
                            // canceled
                            BleLogger.e(TAG, "Query " + id + " interrupted");
                            if (requs.isEmpty()) {
                                handleMtuInterrupted(true, requs.size());
                            }
                            if (!emitter.isDisposed()) {
                                throw ex;
                            }
                        }
                    } else {
                        BleLogger.e(TAG, "Query " + id + " failed. PS-FTP MTU not enabled");
                        throw new BleCharacteristicNotificationNotEnabled("PS-FTP MTU not enabled");
                    }
                }
            } catch (Exception ex) {
                BleLogger.e(TAG, "Query " + id + " failed. Exception: " + ex.getMessage());
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(scheduler);
    }

    public Completable sendNotification(final int id, final byte[] parameters) {
        return sendNotification(id, parameters, Schedulers.newThread());
    }

    /**
     * Sends a single notification to device
     *
     * @param id         one of the PbPFtpHostToDevNotification values
     * @param parameters matching parameter for PbPFtpHostToDevNotification value if any
     * @return Flowable stream <BR>
     * Produces: onError, @see BlePsFtpUtils for possible exceptions <BR>
     * onComplete, once notification has been send <BR>
     */
    public Completable sendNotification(final int id, final byte[] parameters, Scheduler scheduler) {
        return Completable.create(emitter -> {
            try {
                synchronized (pftpNotificationMutex) {
                    if (txInterface.isConnected()) {
                        if (pftpD2HNotificationEnabled.get() == ATT_SUCCESS) {
                            BleLogger.d(TAG, "Send notification id: " + id);
                            resetNotificationPipe();
                            ByteArrayInputStream totalStream = BlePsFtpUtils.makeCompleteMessageStream(parameters != null ? new ByteArrayInputStream(parameters) : null, null, BlePsFtpUtils.MessageType.NOTIFICATION, id);
                            BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
                            List<byte[]> requs = BlePsFtpUtils.buildRfc76MessageFrameAll(totalStream, mtuSize.get(), sequenceNumber);
                            txInterface.transmitMessages(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_H2D_CHARACTERISTIC, requs, false);
                            waitPacketsWritten(notificationPacketsWritten, notificationWaiting, requs.size(), PROTOCOL_TIMEOUT_SECONDS);
                            emitter.onComplete();
                        } else {
                            BleLogger.e(TAG, "Send notification id: " + id + " failed. PS-FTP notification not enabled");
                            throw new BleCharacteristicNotificationNotEnabled("PS-FTP notification not enabled");
                        }
                    } else {
                        BleLogger.e(TAG, "Send notification id: " + id + " failed. BLE disconnected");
                        throw new BleDisconnected();
                    }
                }
            } catch (Exception ex) {
                BleLogger.e(TAG, "Send notification id: " + id + " failed. Exception: " + ex.getMessage());
                if (!emitter.isDisposed()) {
                    emitter.tryOnError(ex);
                }
            }
        }).subscribeOn(scheduler);
    }

    /**
     * Wait endlessly notifications from device using shared Flowable.
     *
     * @return Flowable stream
     * Produces: onNext, for each complete notification received
     * onError, @see BlePsFtpUtils
     * onComplete, non produced
     */
    public Flowable<BlePsFtpUtils.PftpNotificationMessage> waitForNotification(Scheduler scheduler) {
        if (_sharedWaitNotificationFlowable != null) {
            return _sharedWaitNotificationFlowable.subscribeOn(scheduler, false);
        }
        synchronized(pftpWaitNotificationSharedMutex) {
            _sharedWaitNotificationFlowable =
                    waitForNotificationFlowable()
                            .doOnError(
                                    (error) -> _sharedWaitNotificationFlowable = null
                            ).share();
        }
        return _sharedWaitNotificationFlowable.subscribeOn(scheduler, false);
    }

    private Flowable<BlePsFtpUtils.PftpNotificationMessage> waitForNotificationFlowable() {
        return Flowable.create((FlowableOnSubscribe<BlePsFtpUtils.PftpNotificationMessage>) subscriber -> {
                    // NOTE no flush as client may be interested in buffered notifications
                    // notificationInputQueue.clear();
                    synchronized (pftpWaitNotificationMutex) {
                        do {
                            if (pftpD2HNotificationEnabled.get() == ATT_SUCCESS) {
                                try {
                                    synchronized (notificationInputQueue) {
                                        if (notificationInputQueue.isEmpty()) {
                                            notificationInputQueue.wait();
                                        }
                                    }
                                } catch (InterruptedException ex) {
                                    BleLogger.e(TAG, "Wait notification interrupted");
                                    return;
                                }
                            } else {
                                if (!subscriber.isCancelled()) {
                                    subscriber.tryOnError(new BleCharacteristicNotificationNotEnabled("PS-FTP d2h notification not enabled"));
                                }
                                return;
                            }
                            try {
                                Pair<byte[], Integer> packet = notificationInputQueue.take();
                                if (packet.second == 0) {
                                    BlePsFtpUtils.PftpRfc76ResponseHeader response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first);
                                    if (response.next == 0) {
                                        BlePsFtpUtils.PftpNotificationMessage notificationMessage = new BlePsFtpUtils.PftpNotificationMessage();
                                        notificationMessage.id = response.payload[0];
                                        notificationMessage.byteArrayOutputStream.write(response.payload, 1, response.payload.length - 1);
                                        int status = response.status;
                                        while (status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                                            packet = notificationInputQueue.poll(PROTOCOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                                            if (packet != null && packet.second == 0) {
                                                response = BlePsFtpUtils.processRfc76MessageFrameHeader(packet.first);
                                                status = response.status;
                                                BleLogger.d(TAG, "Message frame sub sequent packet successfully received");
                                                notificationMessage.byteArrayOutputStream.write(response.payload, 0, response.payload.length);
                                            } else {
                                                // Failed to receive in timeline
                                                if (!subscriber.isCancelled()) {
                                                    subscriber.tryOnError(new Throwable("Failed to receive notification packet in timeline"));
                                                }
                                                return;
                                            }
                                        }
                                        subscriber.onNext(notificationMessage);
                                    } else {
                                        // not in sync
                                        BleLogger.e(TAG, "wait notification not in sync, take next");
                                    }
                                } else {
                                    if (!subscriber.isCancelled()) {
                                        subscriber.tryOnError(new BleAttributeError("ps-ftp wait notification failure ", packet.second));
                                    }
                                    return;
                                }
                            } catch (InterruptedException ex) {
                                BleLogger.e(TAG, "wait notification interrupted");
                                return;
                            } catch (Exception throwable) {
                                // continue to next notification or stop?
                                if (!subscriber.isCancelled()) {
                                    subscriber.tryOnError(new Exception("Notification receive failed"));
                                }
                                return;
                            }
                        } while (true);
                    }
                }, BackpressureStrategy.BUFFER)
                .onBackpressureBuffer(100, () -> BleLogger.w(TAG, "notifications buffer full"), BackpressureOverflowStrategy.DROP_OLDEST)
                .serialize();
    }

    public Completable waitPsFtpClientReady(final boolean checkConnection) {
        return waitPsFtpClientReady(checkConnection, Schedulers.io());
    }

    public Completable waitPsFtpClientReady(final boolean checkConnection, Scheduler scheduler) {
        Completable mtuEnabled = waitNotificationEnabled(BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, checkConnection, scheduler);
        Completable d2hEnabled = waitNotificationEnabled(BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC, checkConnection, scheduler);
        return Completable.concatArray(mtuEnabled, d2hEnabled);
    }

    @VisibleForTesting
    void readResponse(ByteArrayOutputStream outputStream, final long timeoutSeconds) throws Exception {
        long status = 0;
        int next = 0;
        BlePsFtpUtils.Rfc76SequenceNumber sequenceNumber = new BlePsFtpUtils.Rfc76SequenceNumber();
        BlePsFtpUtils.PftpRfc76ResponseHeader response = new BlePsFtpUtils.PftpRfc76ResponseHeader();
        do {
            // wait for message frame
            if (txInterface.isConnected()) {
                synchronized (mtuInputQueue) {
                    if (mtuInputQueue.isEmpty()) {
                        mtuInputQueue.wait(timeoutSeconds * 1000L);
                    }
                }
            } else {
                // connection lost
                throw new BleDisconnected("Connection lost during read response");
            }
            Pair<byte[], Integer> packet = mtuInputQueue.poll(PROTOCOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (packet != null && packet.second == 0) {
                BlePsFtpUtils.processRfc76MessageFrameHeader(response, packet.first);
                if (sequenceNumber.getSeq() != response.sequenceNumber) {
                    if (response.status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                        byte[] cancelPacket = new byte[]{0x00, 0x00, 0x00};
                        txInterface.transmitMessages(BlePsFtpUtils.RFC77_PFTP_SERVICE, BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC, Collections.singletonList(cancelPacket), true);
                        waitPacketsWritten(packetsWritten, mtuWaiting, 1, timeoutSeconds);
                        BleLogger.d(TAG, "Sequence number mismatch. Stream cancel has been successfully send");
                    }
                    throw new BlePsFtpUtils.PftpResponseError("Air packet lost!", 303);
                }
                sequenceNumber.increment();
                status = response.status;
                if (next == response.next) {
                    next = 1;
                    switch (response.status) {
                        case BlePsFtpUtils.RFC76_STATUS_LAST:
                        case BlePsFtpUtils.RFC76_STATUS_MORE: {
                            outputStream.write(response.payload, 0, response.payload.length);
                            break;
                        }
                        case BlePsFtpUtils.RFC76_STATUS_ERROR_OR_RESPONSE: { // error or response
                            if (response.error == 0) {
                                return;
                            }
                            throw new BlePsFtpUtils.PftpResponseError("Request failed: ", response.error);
                        }
                        default:
                            throw new BlePsFtpUtils.PftpResponseError("Protocol error, undefined status received", 200);
                    }
                } else {
                    throw new BlePsFtpUtils.PftpResponseError("Protocol error stream is out of sync", 200);
                }
            } else {
                handlePacketError(packet);
            }
        } while (status == BlePsFtpUtils.RFC76_STATUS_MORE);
        BleLogger.d(TAG, "RFC76 message has read successfully");
    }

    private void handlePacketError(Pair<byte[], Integer> packet) throws Exception {
        if (!txInterface.isConnected()) {
            // connection lost
            throw new BleDisconnected("Connection lost during packet read");
        } else if (packet == null) {
            throw new BlePsFtpUtils.PftpOperationTimeout("Air packet was not received in required timeline");
        } else {
            throw new BlePsFtpUtils.PftpResponseError("Response error: " + packet.second, packet.second);
        }
    }
}