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
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockkObject

class NAVERBlogTest: StringSpec({
    val api = NAVERBlogParser
    val client = HttpClient(CIO)

    mockkObject(Store.Companion)
    every { Store.Companion.store } returns FakeStore

    "Can check if the URL is a NAVER blog URL" {
        api.isTarget(Url(TestPath.NAVERBlog)) shouldBe true
        api.isTarget(Url(TestPath.NAVERBlogPostView)) shouldBe true

        api.isTarget(Url("https://naver.com/")) shouldBe false
        api.isTarget(Url("https://google.com/")) shouldBe false
        api.isTarget(Url(TestPath.TistoryBlog)) shouldBe false
    }

    "Can convert a NAVER blog URL to a PostView URL" {
        api.convertURLToCrawl(Url(TestPath.NAVERBlog)) shouldBe Url(TestPath.NAVERBlogPostView)
        api.convertURLToCommon(Url(TestPath.NAVERBlogPostView)) shouldBe Url(TestPath.NAVERBlog)
    }

    "Can crawl test page" {
        val crawledData = api.parse(TestCrawler, Url(TestPath.NAVERBlogPostView)).getOrThrow()

        crawledData.second shouldBe listOf(Url("https://huggingface.co/geulgyeol"))

        val post = crawledData.first!!.body

        post.title shouldBe "테스트"
        post.author shouldBe "devngho"
        post.ccl.conditions shouldBe listOf(CCL.CCLConditions.BY)
        post.writtenAt shouldBe 1725387180
        post.markdown shouldBe """https://huggingface\.co/geulgyeol

[geulgyeol \(글결\)](https://huggingface.co/geulgyeol)

테스트 글

![로고](https://postfiles.pstatic.net/MjAyNDEyMDNfMjAz/MDAxNzMzMTU4MjMwMDIz.i_lKab16Uq7ZFi1d4QxozCy7FZJXJ_pU4CQs0nOPnTcg.D5Oi1epOKv8JtWnC203EXRg6wRy_pqNpy1m5_3u3qscg.PNG/Frame_1_transpert.png?type=w966)

---

일반글씨**굵은글씨** 띄어쓰기

*기울인글씨*

++밑줄++

~~취소선~~

여*러++스~~타**일***++~~

다른 문단

## 소**제목**

> 인용구
> 
> **텍스트**

- 목록1
    - 목록**목록2**
    - *목록3*

1. ordered
    1. 목록12
    2. 목록23

```javascript
print('Hello, World')
```

1~1~

1^1^

${"$$"}f\left(x\right)=t^2st${"$$"}

|table4|2|3|
|---|---|---|
||56||
|78||9|

"""
        post.text shouldBe """https://huggingface.co/geulgyeol
geulgyeol (글결)(https://huggingface.co/geulgyeol)
테스트 글

---
일반글씨굵은글씨 띄어쓰기
기울인글씨
밑줄
취소선
여러스타일

다른 문단
소제목
인용구
텍스트
목록1
목록목록2
목록3

ordered
목록12
목록23
print('Hello, World')
11
11
f\left(x\right)=t^2st
table
4 | 2 | 3
 | 5
6 | 
7
8 |  | 9
"""
    }

    "test" {
        val url = Url("https://blog.naver.com/PostView.nhn?blogId=sorijangdo4&logNo=222894441007")

        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
        val post = crawledData.first!!.body

        println(post.title)
        println(post.author)
        println("---")
        println(post.markdown)
        println("---")
        println(post.markdownText)
        println(post.ccl)
        println(crawledData.second)
    }
//
//    "test2" {
//        val url = Url("https://blog.naver.com/PostView.nhn?blogId=godinus&logNo=223685938752")
//        val crawledData = api.parse(TestCrawler, api.convertURLToCrawl(url)).getOrThrow()
//        val post = crawledData.body
//        println(post.title)
//        println(post.author)
//        println(post.markdown)
//        println(post.ccl)
//    }
})
