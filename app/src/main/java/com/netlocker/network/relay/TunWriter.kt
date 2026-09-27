package com.netlocker.network.relay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.FileOutputStream

/**
 * Serializes writes back into the tun device. Multiple relay sessions run on their
 * own coroutines and each may need to write a reply packet at any time, but writing
 * to the same [FileOutputStream] concurrently from multiple threads is asking for
 * interleaved/corrupt packets, so every write goes through this one mutex.
 */
class TunWriter(private val output: FileOutputStream) {
    private val mutex = Mutex()

    suspend fun write(packet: ByteArray, length: Int) {
        mutex.withLock {
            output.write(packet, 0, length)
        }
    }
}
