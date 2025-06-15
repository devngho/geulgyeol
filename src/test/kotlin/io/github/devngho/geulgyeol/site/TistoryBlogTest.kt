package io.github.devngho.geulgyeol.site

import io.github.devngho.geulgyeol.TestCrawler
import io.github.devngho.geulgyeol.TestPath
import io.github.devngho.geulgyeol.data.CCL
import io.github.devngho.geulgyeol.store.FakeStore
import io.github.devngho.geulgyeol.store.Store
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockkObject

class TistoryBlogTest: StringSpec({
    val api = TistoryBlogParser
    val client = HttpClient(CIO)

    mockkObject(Store.Companion)
    every { Store.Companion.store } returns FakeStore

    "Can crawl test page" {
        val crawledData = api.parse(TestCrawler, Url(TestPath.TistoryBlog)).getOrThrow()

        crawledData.second shouldBe listOf(Url("https://huggingface.co/geulgyeol"))

        val post = crawledData.first.body

        post.title shouldBe "테스트 2"
        post.author shouldBe "devngho"
        post.ccl.conditions shouldBe listOf(CCL.CCLConditions.BY)
        post.writtenAt shouldBe 1729809129
        post.markdown shouldBe """asdf

## 제목1 **굵은글씨**

### 제목2

#### 제목3

**굵은글씨**일반글씨++밑줄++*취소선*

> 인용 **굵은 글씨**
> 
> abcd
> 
> efgh
> ijkl mnop
> 

|표|표 안의 **굵은 글씨**|1|
|---|---|---|
||2|3|
|4||5|

[https://huggingface\.co/geulgyeol](https://huggingface.co/geulgyeol)

[geulgyeol \(글결\)](https://huggingface.co/geulgyeol)

- unordered list
    - tab
    - tab2 **굵은글씨**
- asdf

- unordered list

1. ordered list

---

HTML block


```kotlin
fun main() {
    println("Hello, World!")
}
```"""
        post.text shouldBe """asdf
제목1 굵은글씨
제목2
제목3
굵은글씨일반글씨밑줄취소선
인용 굵은 글씨

abcd

efgh
ijkl mnop
표 | 표 안의 굵은 글씨 | 1
 | 2 | 3
4 |  | 5
https://huggingface.co/geulgyeol
geulgyeol (글결)(https://huggingface.co/geulgyeol)
unordered list
tab
tab2 굵은글씨
asdf
unordered list
ordered list
---
HTML block
fun main() {
    println("Hello, World!")
}"""
    }

//    "test" {
//        val url = Url("https://jh-labs.tistory.com/902")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test2" {
//        val url = Url("https://data-newbie.tistory.com/953")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test3" {
//        val url = Url("https://mackger.tistory.com/503")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test4" {
//        val url = Url("https://gbdai.tistory.com/46")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.text)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test5" {
//        val url = Url("https://gbdai.tistory.com/49")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test6" {
//        val url = Url("https://notice.tistory.com/2685")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }
//
//    "test7" {
//        val url = Url("https://grast.tistory.com/3")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.links)
//    }

    "test8" {
        val url = Url("https://s0-cute.tistory.com/7")

        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
        val post = crawledData.first.body

        println(post.title)
        println(post.author)
        println(post.markdown)
        println(post.markdownText)
        println(post.ccl)
        println(crawledData.second)
    }

//    "test9" {
//        val url = Url("https://dyourself.tistory.com/")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url))
//        val post = crawledData.first.getOrThrow().body
//
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//        println(crawledData.second)
//    }

//    "test10" {
//        val url = Url("https://notice.tistory.com/")
//
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url))
//
//        println(crawledData.second)
//    }
})