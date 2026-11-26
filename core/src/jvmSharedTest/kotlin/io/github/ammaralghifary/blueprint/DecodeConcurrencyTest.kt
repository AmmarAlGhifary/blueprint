package io.github.ammaralghifary.blueprint

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author Ammar Al Ghifary */
class DecodeConcurrencyTest {

    @Test
    fun concurrentDecodesOnOneBlueprintOnlySeeTheirOwnErrors() {
        // Reports are collected per thread, so threads sharing a Blueprint must not leak into each other.
        val threads = 8
        val rounds = 40
        val perThread = ConcurrentHashMap<String, AtomicInteger>()
        val shared = Blueprint(hookRegistry, onDecodeError = {
            perThread.getOrPut(Thread.currentThread().name) { AtomicInteger() }.incrementAndGet()
        })
        val start = CountDownLatch(1)
        val failures = ConcurrentHashMap.newKeySet<String>()
        val workers = (1..threads).map { n ->
            Thread({
                start.await()
                // thread n sends n unregistered children per payload
                val children = (1..n).joinToString(",") { """{"type":"u$it"}""" }
                val payload = """{"body":{"type":"column","children":[$children]}}"""
                repeat(rounds) { shared.decodeScreen(payload) }
                val got = perThread[Thread.currentThread().name]?.get() ?: 0
                if (got != n * rounds) failures += "thread $n expected ${n * rounds} but saw $got"
            }, "decode-worker-$n").also { it.start() }
        }
        start.countDown()
        workers.forEach { it.join() }
        assertTrue(failures.toString(), failures.isEmpty())
    }
}
