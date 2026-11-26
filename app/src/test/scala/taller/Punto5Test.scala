package taller

import org.scalatest.funsuite.AnyFunSuite
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

/**
 * Casos propios del punto 5. Ninguno mide tiempos: las cifras que se comparan
 * son cocientes calculados a mano o resultados de `pasoParalelo`, y los tiempos
 * reales se dejan a `App.scala`.
 */
@RunWith(classOf[JUnitRunner])
class Punto5Test extends AnyFunSuite {

  val sim = new SimulacionParticulas()
  import sim._

  def cerca(a: Double, b: Double): Boolean = Math.abs(a - b) < 1e-9

  val vacio: Sistema = Array()
  val una: Sistema = Array(Particula(Vector2(3, -1), Vector2(0.5, -2), 4.0))
  val impar: Sistema = Array.tabulate(13)(i =>
    Particula(Vector2(i, -i), Vector2(1.5, -0.5), i + 0.25))

  // Punto 5 -------------------------------------------------------------------

  test("aceleracion: tiempos fraccionarios dan un cociente fraccionario") {
    assert(cerca(aceleracion(0.75, 0.2), 3.75))
  }

  test("aceleracion: si el paralelo tarda el doble, la aceleración es menor que uno") {
    assert(cerca(aceleracion(9.0, 18.0), 0.5))
    assert(aceleracion(9.0, 18.0) < 1.0)
  }

  test("aceleracion: duplicar el tiempo paralelo reduce a la mitad la aceleración") {
    val base = aceleracion(6.0, 1.5)
    val duplicada = aceleracion(6.0, 3.0)
    assert(cerca(base, 2.0 * duplicada))
  }

  test("aceleracion: la identidad vale también con tiempos no enteros") {
    assert(cerca(aceleracion(2.4, 2.4), 1.0))
  }

  test("umbralConveniente: sin candidatos y sistema vacío devuelve cero") {
    assert(umbralConveniente(vacio, -1.5, Nil) == 0)
  }

  test("umbralConveniente: sin candidatos y una sola partícula devuelve uno") {
    assert(umbralConveniente(una, 0.125, Nil) == 1)
  }

  test("umbralConveniente: sin candidatos devuelve el tamaño, sin importar dt ni los datos") {
    assert(umbralConveniente(impar, 3.75, Nil) == impar.length)
    assert(umbralConveniente(impar, -3.75, Nil) == impar.length)
  }

  test("control: con umbral igual al tamaño, paralelo y secuencial hacen lo mismo") {
    val sistemas = List(vacio, una, impar)
    assert(sistemas.forall { s =>
      val dt = 0.4
      pasoParalelo(s, dt, s.length).sameElements(pasoSecuencial(s, dt)) &&
      cerca(aceleracion(4.8, 4.8), 1.0)
    })
  }
}
