package io.github.devngho.geulgyeol.store

import io.kotest.core.spec.style.StringSpec
import io.ktor.http.Url

class CurrentStoreTest: StringSpec({
//    "test exists" {
//        val path = Url("https://huggingface.co/geulgyeol")
//        println(Store.store.exists(path))
//        Store.store.put(path, Post(
//            url = "test",
//            text = "test",
//            markdown = "test",
//            markdownText = "test",
//            accessedAt = 0,
//            raw = "test",
//            title = "test",
//            author = "test",
//            writtenAt = 0,
//            ccl = CCL(listOf(CCL.CCLConditions.BY), "test")
//        ))
//        println(Store.store.exists(path))
//        println(Store.store.get(path).getOrNull())
//        Store.store.delete(path)
//        println(Store.store.exists(path))
//    }

    "test get" {
        val path = Url("https://blog.naver.com/sohnbc8919/223558580642")
        println(Store.store.get(path).getOrThrow())
    }
})