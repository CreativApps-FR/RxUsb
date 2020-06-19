package dev.creativapps.rxusb

import android.hardware.usb.UsbAccessory
import android.os.ParcelFileDescriptor
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * This class allows you to safely handle communication through an [UsbAccessory] quite simply with
 * Rx style.
 *
 * Created : 13/06/2020.
 * @author CreativApps - Damien P.
 */
abstract class RxUsbAccessory protected constructor(
    protected val accessory: UsbAccessory,
    protected val parcelFileDescriptor: ParcelFileDescriptor,
    protected val bufferByteSize: Int,
    val accessoryName: String
) {

    companion object {
        fun create(
            usbAccessory: UsbAccessory,
            fileDescriptor: ParcelFileDescriptor,
            bufferSize: Int,
            name: String
        ): RxUsbAccessory {
            return RxUsbAccessoryImpl(usbAccessory, fileDescriptor, bufferSize, name)
        }
    }

    /**
     * Native [UsbAccessory] provided by Android to be used.
     */
    val nativeAccessory get() = accessory

    /**
     * Trigger the read dedicated [Thread], listening to an [FileInputStream], and provides a [Flowable]
     * to get continuous incoming data from the accessory.
     *
     * Data is provided as [ByteArray], so any mapping can be done later on the flow.
     *
     * If communication has already been opened, just return the data flow.
     */
    abstract fun openCommunication(backpressureStrategy: BackpressureStrategy): Flowable<ByteArray>

    /**
     * Stop the read dedicated [Thread] and then clean related resources.
     *
     * [openCommunication] must be called again before any other action (read/write) on this accessory.
     */
    abstract fun closeCommunication(): Completable

    /**
     * True if the communication has been opened with [openCommunication], false otherwise.
     */
    abstract fun isCommunicating(): Boolean

    /**
     * Publish the command in the communication opened [FileOutputStream].
     *
     * Emits [CommunicationClosedException] if [openCommunication] has not been called first.
     */
    abstract fun write(command: ByteArray): Completable
}

private class RxUsbAccessoryImpl(
    accessory: UsbAccessory,
    parcelFileDescriptor: ParcelFileDescriptor,
    bufferSize: Int,
    name: String
) : RxUsbAccessory(accessory, parcelFileDescriptor, bufferSize, name) {

    // region PROPERTIES

    private var isOpened = false

    private val readRunnable: Runnable
    private lateinit var readThread: Thread

    private lateinit var readStream: FileInputStream
    private lateinit var writeStream: FileOutputStream

    private lateinit var readPublisher: PublishSubject<ByteArray>

    // endregion

    // region INIT

    init {
        readRunnable = Runnable {
            var byteCountToRead = 0
            val buffer = ByteArray(bufferByteSize)

            // Continuous read
            while (byteCountToRead >= 0 && isOpened) {
                try {
                    byteCountToRead = readStream.read(buffer)
                } catch (e: IOException) {
                    readPublisher.onError(e)
                    close()
                    return@Runnable
                }

                // No message, skip loop
                if (byteCountToRead == 0) {
                    continue
                }

                // Extract message emit it
                val message = buffer.copyOfRange(0, byteCountToRead - 1)
                readPublisher.onNext(message)
            }
        }
    }

    // endregion

    // region IMPLEMENTATION

    override fun openCommunication(backpressureStrategy: BackpressureStrategy): Flowable<ByteArray> {
        if (!isOpened) {
            // Create resources
            val fileDescriptor = parcelFileDescriptor.fileDescriptor
            readStream = FileInputStream(fileDescriptor)
            writeStream = FileOutputStream(fileDescriptor)
            readPublisher = PublishSubject.create()

            // Random thread name
            val threadName = accessoryName + UUID.randomUUID().toString().subSequence(0, 6)
            readThread = Thread(null, readRunnable, threadName)
            isOpened = true

            readThread.start()
        }

        return readPublisher.toFlowable(backpressureStrategy)
    }

    override fun closeCommunication(): Completable {
        return if (!isOpened) {
            Completable.complete()
        } else {
            Completable.create {
                close()
                it.onComplete()
            }
        }
    }

    override fun isCommunicating(): Boolean {
        return isOpened
    }

    override fun write(command: ByteArray): Completable {
        if (!isOpened) {
            return Completable.error(CommunicationClosedException(accessory))
        }

        return Completable.create {
            try {
                writeStream.write(command)
                it.onComplete()
            } catch (e: IOException) {
                it.onError(e)
            }
        }
    }
    // endregion

    // region METHODS

    private fun close() {
        isOpened = false
        try {
            readThread.interrupt()
            parcelFileDescriptor.close()
        } finally {
            // Terminate communication if no errors already
            if (!readPublisher.hasThrowable()) {
                readPublisher.onComplete()
            }
        }
    }

    // endregion
}