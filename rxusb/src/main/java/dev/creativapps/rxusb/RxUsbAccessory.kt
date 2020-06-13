package dev.creativapps.rxusb

import android.hardware.usb.UsbAccessory
import android.os.ParcelFileDescriptor
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable

/**
 * TODO: description
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

    /**
     * Native [UsbAccessory] provided by Android to be used.
     */
    val nativeAccessory get() = accessory

    /**
     * Trigger the read dedicated [Thread], listening to an [FileInputStream], and provides a [Flowable]
     * to get continuous incoming data from the accessory.
     *
     * Data is provided as [ByteArray], so any mapping can be done later on the flow.
     */
    abstract fun openCommunication(backpressureStrategy: BackpressureStrategy): Flowable<ByteArray>

    /**
     * Stop the read dedicated [Thread] and then clean related resources.
     *
     * [openCommunication] must be called before any other action (read/write) on this accessory.
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

    // region IMPLEMENTATION

    override fun openCommunication(backpressureStrategy: BackpressureStrategy): Flowable<ByteArray> {
        TODO("Not yet implemented")
    }

    override fun closeCommunication(): Completable {
        TODO("Not yet implemented")
    }

    override fun isCommunicating(): Boolean {
        TODO("Not yet implemented")
    }

    override fun write(command: ByteArray): Completable {
        TODO("Not yet implemented")
    }
    // endregion

}