# How to write docs?

This is written for Lerna library contributors.

## Scaladoc

We can write a Scaladoc in general ways like below.

```scala mdoc:reset
/**
 * You can write a general scala doc.
 */
class MyClass
```

Moreover, We can write a doctest in a Scaladoc.
For more details of the doctest, see [sbt-doctest](https://github.com/tkawachi/sbt-doctest).
The doctest is intended to use in an example code of a Scaladoc.
In most cases, We should write tests in `src/test/scala` directory.

```scala mdoc:reset
/**
 * You can also write a doctest.
 * {{{
 * scala> val x: Int = 1
 * res0: Int = 1
 * }}}
 */
class MyClass
```


## other docs (like README, etc...)

We can write documents as Markdown-style.
[mdoc](https://scalameta.org/mdoc/) checks documents whether Scala code is compilable or not, etc...
We encourage you to feel free to write a sample code in a document.

We are not using compiled markdown documents by `mdoc`.
Because we are not ready to publish it.
Please stay the source markdown readable without using `mdoc` compile.
