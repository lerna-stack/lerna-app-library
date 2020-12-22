# Lerna Validation

*Lerna Validation* library provides custom validators for [Accord](http://wix.github.io/accord/).

## CustomCombinators

`lerna.validation.CustomCombinators` provides custom validators for *Accord*.
You can use validators like below.

```scala mdoc:reset
import com.wix.accord._
import com.wix.accord.dsl._
import lerna.validation.CustomCombinators.lengthRange

case class Person(bio: String)

implicit val personValidator = validator[Person] { person =>
  // lengthRange is provided by lerna.validation.CustomCombinators
  person.bio is lengthRange(0, 200)
}

val person = Person("I'm a software developer.")
val result: Result = validate(person)
```
