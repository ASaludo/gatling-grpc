package com.github.phisgr.gatling.pb

import com.github.phisgr.gatling.grpc.Predef.$
import com.github.phisgr.pb.complex.complex.{Bar, Foo}
import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.validation.{Failure, Success}
import io.gatling.core.session.{Expression, Session}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LensExprSpec extends AnyFlatSpec with Matchers with StrictLogging {
  private val session = Session(
    scenario = "Scenario",
    userId = 1L,
    eventLoop = null
  ).setAll(
    "bar" -> Bar(baz = 1),
    "baz" -> 2,
    "bars" -> List(Bar(baz = 5), Bar(baz = 6)),
    "stringBar" -> ("bar" -> Bar(baz = 10)),
    "stringBarMap" -> Map("barr" -> Bar(baz = 11)),
    "generics" -> List("erasure", "BOOM!")
  )

  "Setting with lens" should "work" in {

    val barE = Bar.defaultInstance.updateExpr(_.baz :~ $("baz"))
    barE(session) shouldBe Success(Bar(2))

    val fooE = Foo(bar = List(Bar(100))).updateExpr(
      _.bar(0).baz :~ $("baz")
    )
    fooE(session) shouldBe Success(Foo(bar = List(Bar(2))))
  }

  "Modifying with lens" should "work" in {
    val barE = $[Bar]("bar")
      .updateExpr(_.baz.modifyExpr(
        $[Int]("baz").map(sessionValue => _ - sessionValue)
      ))
    barE(session) shouldBe Success(Bar(-1))
  }

  "Seq lens" should "work" in {
    val fooE = Foo.defaultInstance.updateExpr(
      _.bar :+~ Bar.defaultInstance.updateExpr(_.baz :~ $("baz")),
      _.bar :+~ $("bar"),
      _.bar :++~ $("bars")
    )

    val foo = Foo(
      bar = List(
        Bar(2), Bar(1), Bar(5), Bar(6)
      )
    )
    fooE(session) shouldBe Success(foo)

    fooE.updateExpr(
      _.bar.foreachExpr(_.baz :~ $("baz"))
    )(session) shouldBe Success(
      Foo(
        bar = List(
          Bar(2), Bar(2), Bar(2), Bar(2)
        )
      )
    )

    fooE.updateExpr(
      _.bar(2).baz :~ $("baz")
    )(session) shouldBe Success(
      Foo(
        bar = List(
          Bar(2), Bar(1), Bar(2), Bar(6)
        )
      )
    )
  }

  "Set lens" should "work" in {
    val fooE = Foo.defaultInstance.updateExpr(
      _.barSet :+~ Bar.defaultInstance.updateExpr(_.baz :~ $("baz")),
      _.barSet :+~ $[Bar]("bar"),
      _.barSet :++~ $[List[Bar]]("bars")
    )

    val foo = Foo(
      barSet = Set(
        Bar(1), Bar(2), Bar(5), Bar(6)
      )
    )
    fooE(session) shouldBe Success(foo)

    fooE.updateExpr(
      _.barSet.foreachExpr(_.baz :~ $("baz"))
    )(session) shouldBe Success(
      Foo(barSet = Set(Bar(2)))
    )
  }

  "Map lens" should "work" in {
    val fooE = Foo.defaultInstance.updateExpr(
      _.barMap :+~ $("stringBar"),
      _.barMap :++~ $[List[String]]("generics").map(_.map { s => s -> Bar(s.length) }),
      _.barMap :++~ $("stringBarMap")
    )

    val foo = Foo(
      barMap = Map(
        "bar" -> Bar(10),
        "barr" -> Bar(11),
        "erasure" -> Bar(7),
        "BOOM!" -> Bar(5)
      )
    )
    fooE(session) shouldBe Success(foo)

    fooE.updateExpr(
      _.barMap.foreachExpr(_ :~ $("stringBar"))
    )(session) shouldBe Success(
      Foo(barMap = Map("bar" -> Bar(baz = 10)))
    )

    fooE.updateExpr(
      _.barMap.foreachValueExpr(_.baz :~ $("baz"))
    )(session) shouldBe Success(Foo(
      barMap = Map(
        "bar" -> Bar(2),
        "barr" -> Bar(2),
        "erasure" -> Bar(2),
        "BOOM!" -> Bar(2)
      )
    ))
  }

  "Wrong session variables" should "fail" in {
    Bar.defaultInstance.updateExpr(_.baz :~ $("bar"))(session) shouldBe
      Failure("Value Bar(1,UnknownFieldSet(Map())) is of type com.github.phisgr.pb.complex.complex.Bar, expected java.lang.Integer")
    Bar.defaultInstance.updateExpr(_.baz :~ $("nonExisting"))(session) shouldBe
      Failure("No attribute named 'nonExisting' is defined")
  }

  "Erased generics" should "be unsafe" in {
    def assertClassCastException(fooE: Expression[Foo]): Unit = {
      val foo = fooE(session).asInstanceOf[Success[Foo]].value
      logger.warn("We have a malformed object: {}", foo)
      // Notice that the error is thrown after the expression is evaluated
      an[ClassCastException] should be thrownBy foo.toByteArray
    }

    assertClassCastException(
      Foo(bar = List(Bar(1))).updateExpr(_.bar :++~ $("generics"))
    )

    import io.gatling.core.Predef.stringToExpression
    assertClassCastException(
      Foo(bar = List(Bar(1))).updateExpr(_.bar :++~ "#{generics}")
    )
  }

}
