package com.example.cloudmusic.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkType {
    WIFI,       // Wi-Fi — 버퍼/캐시 풀 사용
    CELLULAR,   // 모바일 데이터 — 버퍼 줄이고 데이터 절약
    NONE,       // 오프라인
}

/**
 * 현재 네트워크 타입을 Flow로 방출한다.
 * Wi-Fi ↔ 모바일 데이터 전환 시 즉시 갱신돼서,
 * ExoPlayer 버퍼 크기를 자동으로 조정하는 데 쓰인다.
 */
class NetworkMonitor(context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val networkType: Flow<NetworkType> = callbackFlow {
        // 초기값: 현재 상태를 즉시 방출
        trySend(currentNetworkType())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentNetworkType()) }
            override fun onLost(network: Network)      { trySend(currentNetworkType()) }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) { trySend(currentNetworkType()) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentNetworkType(): NetworkType {
        val network = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
            else -> NetworkType.NONE
        }
    }
}
