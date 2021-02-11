package kscience.kmath.ast

import kscience.kmath.complex.Complex
import kscience.kmath.complex.ComplexField
import kscience.kmath.expressions.invoke
import kscience.kmath.operations.Algebra
import kscience.kmath.operations.RealField
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ParserTest {
    @Test
    fun `evaluate MST`() {
        val mst = "2+2*(2+2)".parseMath()
        val res = ComplexField.evaluate(mst)
        assertEquals(Complex(10.0, 0.0), res)
    }

    @Test
    fun `evaluate MSTExpression`() {
        val res = ComplexField.mstInField { number(2) + number(2) * (number(2) + number(2)) }()
        assertEquals(Complex(10.0, 0.0), res)
    }

    @Test
    fun `evaluate MST with singular`() {
        val mst = "i".parseMath()
        val res = ComplexField.evaluate(mst)
        assertEquals(ComplexField.i, res)
    }


    @Test
    fun `evaluate MST with unary function`() {
        val mst = "sin(0)".parseMath()
        val res = RealField.evaluate(mst)
        assertEquals(0.0, res)
    }

    @Test
    fun `evaluate MST with binary function`() {
        val magicalAlgebra = object : Algebra<String> {
            override fun symbol(value: String): String = value

            override fun unaryOperationFunction(operation: String): (arg: String) -> String {
                throw NotImplementedError()
            }

            override fun binaryOperationFunction(operation: String): (left: String, right: String) -> String =
                when (operation) {
                    "magic" -> { left, right -> "$left ★ $right" }
                    else -> throw NotImplementedError()
                }
        }

        val mst = "magic(a, b)".parseMath()
        val res = magicalAlgebra.evaluate(mst)
        assertEquals("a ★ b", res)
    }
}
