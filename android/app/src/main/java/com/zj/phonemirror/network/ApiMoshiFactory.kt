// 创建支持 Kotlin data class 反射序列化的 API Moshi 实例。
package com.zj.phonemirror.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** 集中配置 Retrofit 请求与响应共用的 JSON 适配器。 */
object ApiMoshiFactory {
    /** 注册 Kotlin 反射适配器，并保留显式添加适配器的优先级。 */
    fun create(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
}
