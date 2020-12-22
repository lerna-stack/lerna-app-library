package lerna.validation

import com.wix.accord._
import com.wix.accord.dsl._
import lerna.validation.CustomCombinators._
import lerna.validation.TestValidator.TestModel

@SuppressWarnings(
  Array(
    "org.wartremover.contrib.warts.MissingOverride",
  ),
)
object TestValidator {

  final case class TestModel(data: String)

  implicit val hankakuEiSujiKigoValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is `半角英数字記号`
  }

  implicit val hankakuEiSujiValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is `半角英数字`
  }

  implicit val hankakuValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is `半角`
  }

  implicit val zenkakuValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is `全角`
  }

  implicit val hankakusujiValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is `半角数字`
  }

  implicit val lengthRangeValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is lengthRange(3, 5)
  }

  implicit val decimalValidator: Validator[TestModel] = validator[TestModel] { model =>
    model.data is decimal(4, 13, 2)
  }

}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
  ),
)
class CustomCombinatorsSpec extends LernaValidationBaseSpec {

  var model = TestModel("")

  "半角英数字記号" should {

    import TestValidator.hankakuEiSujiKigoValidator

    "OK" in {

      model = TestModel("1a@")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("ｱ")
      expect(validate(model).isFailure)

      model = TestModel("あ")
      expect(validate(model).isFailure)

    }

  }

  "半角英数字" should {

    import TestValidator.hankakuEiSujiValidator

    "OK" in {

      model = TestModel("1a")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("@1a")
      expect(validate(model).isFailure)

      model = TestModel("1a@")
      expect(validate(model).isFailure)

      model = TestModel("ｱ")
      expect(validate(model).isFailure)

      model = TestModel("あ")
      expect(validate(model).isFailure)

    }

  }

  "半角" should {

    import TestValidator.hankakuValidator

    "OK" in {

      model = TestModel("1a@ｱ")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("あ")
      expect(validate(model).isFailure)

    }

  }

  "全角" should {

    import TestValidator.zenkakuValidator

    "OK" in {

      model = TestModel("あ")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("あ1")
      expect(validate(model).isFailure)

      model = TestModel("あa")
      expect(validate(model).isFailure)

      model = TestModel("あ@")
      expect(validate(model).isFailure)

      model = TestModel("あｱ")
      expect(validate(model).isFailure)

    }

  }

  "半角数字" should {

    import TestValidator.hankakusujiValidator

    "OK" in {

      model = TestModel("0123456789")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("1a")
      expect(validate(model).isFailure)

      model = TestModel("01234５6789")
      expect(validate(model).isFailure)

    }

  }

  "lengthRange" should {

    import TestValidator.lengthRangeValidator

    "OK" in {

      model = TestModel("123")
      expect(validate(model).isSuccess)

      model = TestModel("12345")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("12")
      expect(validate(model).isFailure)

      model = TestModel("123456")
      expect(validate(model).isFailure)

    }

  }

  "decimal" should {

    import TestValidator.decimalValidator

    "OK" in {

      model = TestModel("1234.12")
      expect(validate(model).isSuccess)

      model = TestModel("1234567890123.12")
      expect(validate(model).isSuccess)

    }

    "NG" in {

      model = TestModel("123.12")
      expect(validate(model).isFailure)

      model = TestModel("12345678901234.12")
      expect(validate(model).isFailure)

      model = TestModel("1234567890123.1")
      expect(validate(model).isFailure)

      model = TestModel("1234567890123.123")
      expect(validate(model).isFailure)

      model = TestModel("1234a12")
      expect(validate(model).isFailure)
    }

  }
}
