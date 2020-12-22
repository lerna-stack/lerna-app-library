package lerna.log.logback.converter

import lerna.log.LernaLogBaseSpec

final class ConverterUtilSpec extends LernaLogBaseSpec {
  "ConverterUtil" should {
    "convert multiple lines string to single line string" in {
      val src = "abc\\def\r\nghi\njkl\tmno"
      val dst = ConverterUtil.toOneline(src)
      dst shouldBe "abc\\\\def\\nghi\\njkl    mno"
    }
  }
}
