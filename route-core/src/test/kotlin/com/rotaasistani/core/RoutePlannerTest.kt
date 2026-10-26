package com.rotaasistani.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutePlannerTest {

    private fun euclidean(points: List<Pair<Double, Double>>): Array<DoubleArray> =
        Array(points.size) { i ->
            DoubleArray(points.size) { j ->
                hypot(points[i].first - points[j].first, points[i].second - points[j].second)
            }
        }

    private fun assertPermutation(order: List<Int>, n: Int, start: Int = 0) {
        assertEquals((0 until n).filter { it != start }.toSet(), order.toSet())
        assertEquals(n - 1, order.size)
    }

    private fun bruteForce(cost: Array<DoubleArray>): Double {
        var best = Double.MAX_VALUE
        fun go(prev: Int, remaining: List<Int>, acc: Double) {
            if (acc >= best) return
            if (remaining.isEmpty()) {
                best = acc
                return
            }
            for (node in remaining) go(node, remaining - node, acc + cost[prev][node])
        }
        go(0, (1 until cost.size).toList(), 0.0)
        return best
    }

    @Test
    fun `tek ve iki dugumlu girdiler`() {
        assertEquals(emptyList(), RoutePlanner.plan(arrayOf(doubleArrayOf(0.0))).order)
        val two = RoutePlanner.plan(arrayOf(doubleArrayOf(0.0, 5.0), doubleArrayOf(5.0, 0.0)))
        assertEquals(listOf(1), two.order)
        assertEquals(5.0, two.cost)
    }

    @Test
    fun `dogru uzerindeki duraklar sirayla gezilir`() {
        val random = Random(1)
        val xs = (1..15).map { it.toDouble() }.shuffled(random)
        val points = listOf(0.0 to 0.0) + xs.map { it to 0.0 }
        val plan = RoutePlanner.plan(euclidean(points))
        assertPermutation(plan.order, points.size)
        assertEquals(15.0, plan.cost, 1e-9)
        assertEquals((1..15).map { it.toDouble() }, plan.order.map { points[it].first })
    }

    @Test
    fun `kucuk orneklerde kaba kuvvet optimumunu bulur`() {
        val random = Random(42)
        repeat(40) {
            val points = List(8) { random.nextDouble() * 100 to random.nextDouble() * 100 }
            val matrix = euclidean(points)
            val plan = RoutePlanner.plan(matrix)
            assertPermutation(plan.order, points.size)
            val optimum = bruteForce(matrix)
            assertTrue(plan.cost <= optimum + 1e-6, "plan=${plan.cost} optimum=$optimum")
        }
    }

    @Test
    fun `asimetrik matriste de optimumu bulur ve maliyeti dogru raporlar`() {
        val random = Random(7)
        repeat(30) {
            val n = 8
            val matrix = Array(n) { i -> DoubleArray(n) { j -> if (i == j) 0.0 else 1.0 + random.nextDouble() * 50 } }
            val plan = RoutePlanner.plan(matrix)
            assertPermutation(plan.order, n)
            assertEquals(RoutePlanner.pathCost(matrix, 0, plan.order), plan.cost, 1e-9)
            val optimum = bruteForce(matrix)
            assertTrue(plan.cost <= optimum + 1e-6, "plan=${plan.cost} optimum=$optimum")
        }
    }

    @Test
    fun `sezgisel cozucu kesin cozume cok yakin`() {
        val random = Random(11)
        repeat(25) {
            val points = List(11) { random.nextDouble() * 100 to random.nextDouble() * 100 }
            val matrix = euclidean(points)
            val exact = RoutePlanner.plan(matrix)
            val heuristic = RoutePlanner.plan(matrix, exactLimit = 0)
            assertPermutation(heuristic.order, points.size)
            assertEquals(RoutePlanner.pathCost(matrix, 0, heuristic.order), heuristic.cost, 1e-9)
            assertTrue(heuristic.cost <= exact.cost * 1.03, "sezgisel=${heuristic.cost} kesin=${exact.cost}")
            assertTrue(exact.cost <= heuristic.cost + 1e-6)
        }
    }

    @Test
    fun `izgara uzerinde optimuma yakin yilan rota bulur`() {
        // 8x8 ızgara, başlangıç köşede: optimum açık rota 63 birimdir.
        val points = (0 until 8).flatMap { y -> (0 until 8).map { x -> x.toDouble() to y.toDouble() } }
        val shuffled = listOf(points.first()) + points.drop(1).shuffled(Random(3))
        val plan = RoutePlanner.plan(euclidean(shuffled))
        assertPermutation(plan.order, shuffled.size)
        assertTrue(plan.cost <= 63.0 * 1.03, "maliyet=${plan.cost}")
    }

    @Test
    fun `65 durakli mahalle senaryosunda en yakin komsudan iyi ve hizli`() {
        val random = Random(2026)
        val points = List(66) { random.nextDouble() * 2000 to random.nextDouble() * 2000 }
        val matrix = euclidean(points)
        val started = System.nanoTime()
        val plan = RoutePlanner.plan(matrix)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertPermutation(plan.order, points.size)
        val greedy = RoutePlanner.pathCost(matrix, 0, RoutePlanner.nearestNeighborOrder(matrix))
        val listOrder = RoutePlanner.pathCost(matrix, 0, (1 until points.size).toList())
        println("65 durak: plan=%.0f, en-yakın=%.0f, liste=%.0f, süre=%d ms".format(plan.cost, greedy, listOrder, elapsedMs))
        assertTrue(plan.cost < greedy)
        assertTrue(plan.cost < listOrder * 0.5)
        assertTrue(elapsedMs < 5_000)
    }

    @Test
    fun `haversine bilinen mesafeyi verir`() {
        // 1 derece enlem ≈ 111.195 km
        val d = Geo.haversineMeters(LatLng(40.0, 29.0), LatLng(41.0, 29.0))
        assertTrue(abs(d - 111_195) < 10, "d=$d")
    }
}
