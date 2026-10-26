package com.rotaasistani.core

import kotlin.math.min
import kotlin.random.Random

/**
 * Tek kuryenin "bulunduğu yerden başla, tüm durakları gez, son durakta bitir"
 * problemi (açık uçlu Gezgin Satıcı Problemi) için sezgisel çözücü.
 *
 * Yöntem: en yakın komşu ile başlangıç rotası, ardından 2-opt ve Or-opt yerel
 * aramaları. Yerel optimumdan kurtulmak için "double-bridge" sarsmalı
 * Iterated Local Search uygulanır. Maliyet matrisi asimetrik olabilir
 * (tek yön sokaklar, yol ağı süreleri), hamle maliyetleri buna göre hesaplanır.
 *
 * Az sayıda durakta (ör. ertelenen paketler turu) Held-Karp dinamik
 * programlama ile kesin en kısa sıra bulunur.
 *
 * Matrisin 0. satırı/sütunu genelde kuryenin konumudur; dönen sıra bu
 * başlangıç düğümünü içermez.
 */
object RoutePlanner {

    data class Plan(val order: List<Int>, val cost: Double)

    private const val EPS = 1e-9
    const val EXACT_LIMIT = 12

    fun plan(
        cost: Array<DoubleArray>,
        start: Int = 0,
        kicks: Int = 300,
        seed: Long = 7L,
        exactLimit: Int = EXACT_LIMIT,
    ): Plan {
        val n = cost.size
        require(n > 0 && cost.all { it.size == n }) { "Maliyet matrisi kare olmalı" }
        require(start in 0 until n) { "Başlangıç düğümü matris dışında" }
        val nodes = (0 until n).filter { it != start }
        if (nodes.size <= 1) return Plan(nodes, pathCost(cost, start, nodes))
        if (nodes.size <= exactLimit) return exact(cost, start, nodes)

        val search = LocalSearch(cost, start)
        var best = nearestNeighborOrder(cost, start, nodes).toIntArray()
        search.improve(best)
        var bestCost = search.cost(best)

        if (nodes.size >= 4) {
            val random = Random(seed)
            var current = best.copyOf()
            var currentCost = bestCost
            repeat(kicks) {
                val candidate = doubleBridge(current, random)
                search.improve(candidate)
                val candidateCost = search.cost(candidate)
                if (candidateCost < currentCost - EPS) {
                    current = candidate
                    currentCost = candidateCost
                    if (candidateCost < bestCost - EPS) {
                        best = candidate.copyOf()
                        bestCost = candidateCost
                    }
                }
            }
        }
        return Plan(best.toList(), bestCost)
    }

    /** Verilen sırayla gidildiğinde toplam maliyet. */
    fun pathCost(cost: Array<DoubleArray>, start: Int, order: List<Int>): Double {
        var total = 0.0
        var prev = start
        for (node in order) {
            total += cost[prev][node]
            prev = node
        }
        return total
    }

    /** "Her seferinde en yakın paketine git" sezgisi; karşılaştırma için de kullanılır. */
    fun nearestNeighborOrder(
        cost: Array<DoubleArray>,
        start: Int = 0,
        nodes: List<Int> = cost.indices.filter { it != start },
    ): List<Int> {
        val remaining = nodes.toMutableList()
        val order = ArrayList<Int>(nodes.size)
        var current = start
        while (remaining.isNotEmpty()) {
            var bestIndex = 0
            var bestCost = Double.MAX_VALUE
            for ((index, node) in remaining.withIndex()) {
                val c = cost[current][node]
                if (c < bestCost) {
                    bestCost = c
                    bestIndex = index
                }
            }
            current = remaining.removeAt(bestIndex)
            order.add(current)
        }
        return order
    }

    /** Held-Karp: O(m² · 2^m), m ≤ [EXACT_LIMIT] için anlık. */
    private fun exact(cost: Array<DoubleArray>, start: Int, nodes: List<Int>): Plan {
        val m = nodes.size
        val full = (1 shl m) - 1
        val dp = Array(1 shl m) { DoubleArray(m) { Double.POSITIVE_INFINITY } }
        val parent = Array(1 shl m) { IntArray(m) { -1 } }
        for (j in 0 until m) dp[1 shl j][j] = cost[start][nodes[j]]
        for (mask in 1..full) {
            for (j in 0 until m) {
                if ((mask and (1 shl j)) == 0) continue
                val base = dp[mask][j]
                if (base == Double.POSITIVE_INFINITY) continue
                for (k in 0 until m) {
                    if ((mask and (1 shl k)) != 0) continue
                    val next = mask or (1 shl k)
                    val value = base + cost[nodes[j]][nodes[k]]
                    if (value < dp[next][k]) {
                        dp[next][k] = value
                        parent[next][k] = j
                    }
                }
            }
        }
        var end = 0
        for (j in 1 until m) if (dp[full][j] < dp[full][end]) end = j
        val order = ArrayList<Int>(m)
        var mask = full
        var current = end
        while (current >= 0) {
            order.add(nodes[current])
            val previous = parent[mask][current]
            mask = mask xor (1 shl current)
            current = previous
        }
        order.reverse()
        return Plan(order, pathCost(cost, start, order))
    }

    /** Rotayı A|B|C|D parçalarına bölüp A C B D yapar. */
    private fun doubleBridge(route: IntArray, random: Random): IntArray {
        val n = route.size
        val a = random.nextInt(0, n - 2)
        val b = random.nextInt(a + 1, n - 1)
        val c = random.nextInt(b + 1, n)
        return route.copyOfRange(0, a) +
            route.copyOfRange(b, c) +
            route.copyOfRange(a, b) +
            route.copyOfRange(c, n)
    }

    private class LocalSearch(private val cost: Array<DoubleArray>, private val start: Int) {

        /** b < 0 "rotanın sonu" anlamına gelir; açık uçlu rotada dönüş maliyeti yoktur. */
        private fun c(a: Int, b: Int): Double = if (b < 0) 0.0 else cost[a][b]

        fun cost(route: IntArray): Double {
            var total = 0.0
            var prev = start
            for (node in route) {
                total += cost[prev][node]
                prev = node
            }
            return total
        }

        fun improve(route: IntArray) {
            var guard = 0
            while (guard++ < 10_000) {
                val twoOptImproved = twoOpt(route)
                val orOptImproved = orOpt(route)
                if (!twoOptImproved && !orOptImproved) break
            }
        }

        /** fwd[k]: route[0..k] ileri yön toplamı, bwd[k]: aynı kenarların ters yön toplamı. */
        private fun prefix(route: IntArray, fwd: DoubleArray, bwd: DoubleArray) {
            fwd[0] = 0.0
            bwd[0] = 0.0
            for (k in 1 until route.size) {
                fwd[k] = fwd[k - 1] + cost[route[k - 1]][route[k]]
                bwd[k] = bwd[k - 1] + cost[route[k]][route[k - 1]]
            }
        }

        /** route[i..j] parçasını ters çevirerek kazanç arar. */
        private fun twoOpt(route: IntArray): Boolean {
            val n = route.size
            if (n < 2) return false
            val fwd = DoubleArray(n)
            val bwd = DoubleArray(n)
            prefix(route, fwd, bwd)
            var improved = false
            for (i in 0 until n - 1) {
                for (j in i + 1 until n) {
                    val p = if (i == 0) start else route[i - 1]
                    val q = if (j == n - 1) -1 else route[j + 1]
                    val before = cost[p][route[i]] + (fwd[j] - fwd[i]) + c(route[j], q)
                    val after = cost[p][route[j]] + (bwd[j] - bwd[i]) + c(route[i], q)
                    if (after < before - EPS) {
                        route.reverse(i, j + 1)
                        prefix(route, fwd, bwd)
                        improved = true
                    }
                }
            }
            return improved
        }

        /** 1-3 duraklık bir parçayı (gerekirse ters çevirip) başka bir yere taşır. */
        private fun orOpt(route: IntArray): Boolean {
            val n = route.size
            if (n < 3) return false
            val fwd = DoubleArray(n)
            val bwd = DoubleArray(n)
            prefix(route, fwd, bwd)
            for (len in 1..min(3, n - 1)) {
                for (i in 0..n - len) {
                    val k = i + len - 1
                    val p = if (i == 0) start else route[i - 1]
                    val q = if (k == n - 1) -1 else route[k + 1]
                    val segmentForward = fwd[k] - fwd[i]
                    val segmentBackward = bwd[k] - bwd[i]
                    val removalGain = cost[p][route[i]] + segmentForward + c(route[k], q) - c(p, q)
                    for (t in -1 until n) {
                        if (t in (i - 1)..k) continue
                        val a = if (t == -1) start else route[t]
                        val b = if (t + 1 < n) route[t + 1] else -1
                        val base = c(a, b)
                        val forward = c(a, route[i]) + segmentForward + c(route[k], b) - base
                        if (forward < removalGain - EPS) {
                            move(route, i, k, t, reversed = false)
                            return true
                        }
                        if (len > 1) {
                            val backward = c(a, route[k]) + segmentBackward + c(route[i], b) - base
                            if (backward < removalGain - EPS) {
                                move(route, i, k, t, reversed = true)
                                return true
                            }
                        }
                    }
                }
            }
            return false
        }

        /** route[i..k] parçasını, orijinal dizideki t indeksli elemanın arkasına taşır. */
        private fun move(route: IntArray, i: Int, k: Int, t: Int, reversed: Boolean) {
            val len = k - i + 1
            val segment = route.copyOfRange(i, k + 1)
            if (reversed) segment.reverse()
            val rest = IntArray(route.size - len)
            var w = 0
            for (x in route.indices) if (x < i || x > k) rest[w++] = route[x]
            val insertAt = if (t < i) t + 1 else t + 1 - len
            var r = 0
            for (x in 0 until insertAt) route[r++] = rest[x]
            for (s in segment) route[r++] = s
            for (x in insertAt until rest.size) route[r++] = rest[x]
        }
    }
}
