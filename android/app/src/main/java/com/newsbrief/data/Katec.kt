package com.newsbrief.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * GPS 좌표(WGS84)를 오피넷이 쓰는 KATEC 좌표로 바꾼다.
 *
 * 오피넷의 반경 검색은 위경도를 받지 않고 국내 좌표계만 받는다.
 * 베셀 타원체 기준 TM 투영이라, 타원체 변환(3매개변수)까지 거쳐야 값이 맞는다.
 */
object Katec {

    fun fromWgs84(latitude: Double, longitude: Double): Pair<Double, Double> {
        // 1) WGS84 위경도 → 지구중심 직교좌표
        val aW = 6_378_137.0
        val fW = 1 / 298.257223563
        val e2W = fW * (2 - fW)

        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val nW = aW / sqrt(1 - e2W * sin(phi).pow(2))

        // 2) 한국 표준 3매개변수 이동 (WGS84 → 베셀)
        val x = nW * cos(phi) * cos(lambda) + DX
        val y = nW * cos(phi) * sin(lambda) + DY
        val z = nW * (1 - e2W) * sin(phi) + DZ

        // 3) 직교좌표 → 베셀 위경도
        val e2 = F_B * (2 - F_B)
        val ep2 = e2 / (1 - e2)
        val b = A_B * (1 - F_B)
        val r = sqrt(x * x + y * y)
        val theta = atan2(z * A_B, r * b)
        val phiB = atan2(
            z + ep2 * b * sin(theta).pow(3),
            r - e2 * A_B * cos(theta).pow(3),
        )
        val lambdaB = atan2(y, x)

        // 4) 베셀 위경도 → TM 투영
        val a0 = 1 - e2 / 4 - 3 * e2.pow(2) / 64 - 5 * e2.pow(3) / 256
        val a2 = 3.0 / 8 * (e2 + e2.pow(2) / 4 + 15 * e2.pow(3) / 128)
        val a4 = 15.0 / 256 * (e2.pow(2) + 3 * e2.pow(3) / 4)
        val a6 = 35 * e2.pow(3) / 3072

        fun meridian(p: Double) =
            A_B * (a0 * p - a2 * sin(2 * p) + a4 * sin(4 * p) - a6 * sin(6 * p))

        val n = A_B / sqrt(1 - e2 * sin(phiB).pow(2))
        val t = tan(phiB).pow(2)
        val c = ep2 * cos(phiB).pow(2)
        val aa = (lambdaB - LON0) * cos(phiB)

        val east = X0 + K0 * n * (
            aa + (1 - t + c) * aa.pow(3) / 6 +
                (5 - 18 * t + t * t + 72 * c - 58 * ep2) * aa.pow(5) / 120
            )
        val north = Y0 + K0 * (
            meridian(phiB) - meridian(LAT0) + n * tan(phiB) * (
                aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
                    (61 - 58 * t + t * t + 600 * c - 330 * ep2) * aa.pow(6) / 720
                )
            )
        return east to north
    }

    /** 베셀 타원체 */
    private const val A_B = 6_377_397.155
    private const val F_B = 1 / 299.1528128

    /** WGS84 → 베셀(한국) 이동량 */
    private const val DX = -146.43
    private const val DY = 507.89
    private const val DZ = 681.46

    /** KATEC 투영 기준 */
    private val LAT0 = Math.toRadians(38.0)
    private val LON0 = Math.toRadians(128.0)
    private const val K0 = 0.9999
    private const val X0 = 400_000.0
    private const val Y0 = 600_000.0
}
