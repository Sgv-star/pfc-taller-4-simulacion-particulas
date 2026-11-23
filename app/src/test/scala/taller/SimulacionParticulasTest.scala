package taller

import org.scalatest.funsuite.AnyFunSuite
import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import scala.util.Random

/**
 * Las pruebas no miden tiempos: un tiempo depende de la máquina y de lo que
 * esté corriendo al lado, así que una prueba que lo compare fallaría sin que
 * nada esté mal.
 */
@RunWith(classOf[JUnitRunner])
class SimulacionParticulasTest extends AnyFunSuite {

  val sim = new SimulacionParticulas()
  import sim._

  val eps = 1e-9

  val p = Particula(Vector2(0, 0), Vector2(1, 2), 1.0)

  val s: Sistema = Array(
    Particula(Vector2(0, 0), Vector2(3, 4), 2.0), // norma 5
    Particula(Vector2(4, 0), Vector2(0, 0), 2.0))

  val vacio: Sistema = Array()

  val azar = new Random(7)
  val grande: Sistema = Array.fill(1000)(Particula(
    Vector2(azar.nextDouble() * 10, azar.nextDouble() * 10),
    Vector2(azar.nextDouble() * 2, azar.nextDouble() * 2),
    azar.nextDouble() * 5 + 0.1))

  def cerca(a: Double, b: Double): Boolean = Math.abs(a - b) < 1e-6

  def cerca(a: Vector2, b: Vector2): Boolean =
    Math.abs(a.x - b.x) < 1e-6 && Math.abs(a.y - b.y) < 1e-6

  // Punto 1 -------------------------------------------------------------------

  test("pasoSecuencial: la posición avanza velocidad por dt") {
    assert(pasoSecuencial(Array(p), 2.0).head.pos == Vector2(2.0, 4.0))
  }

  test("pasoSecuencial: con dt cero nada se mueve") {
    assert(pasoSecuencial(Array(p), 0.0).head.pos == Vector2(0.0, 0.0))
  }

  test("pasoSecuencial: la velocidad y la masa no cambian") {
    val despues = pasoSecuencial(Array(p), 3.0).head
    assert(despues.vel == p.vel && despues.masa == p.masa)
  }

  test("pasoSecuencial: sobre el sistema vacío no hay nada que mover") {
    assert(pasoSecuencial(vacio, 1.0).isEmpty)
  }

  test("pasoSecuencial: cada partícula usa su propia posición y su propia velocidad") {
    val trio: Sistema = Array(
      Particula(Vector2(1, 1), Vector2(2, 0), 1.0),
      Particula(Vector2(-2, 5), Vector2(0, -1), 2.0),
      Particula(Vector2(0.5, 0.5), Vector2(-1, -1), 3.0))
    val esperado = Array(Vector2(7.0, 1.0), Vector2(-2.0, 2.0), Vector2(-2.5, -2.5))
    assert(pasoSecuencial(trio, 3.0).map(_.pos).sameElements(esperado))
  }

  test("pasoSecuencial: avanzar dt y retroceder -dt devuelve las posiciones originales") {
    val ida = pasoSecuencial(grande, 0.7)
    val vuelta = pasoSecuencial(ida, -0.7)
    assert(vuelta.zip(grande).forall { case (a, b) => cerca(a.pos, b.pos) })
  }

  test("pasoSecuencial: en todo el sistema se conservan las velocidades y las masas") {
    val despues = pasoSecuencial(grande, 1.5)
    assert(despues.length == grande.length &&
      despues.zip(grande).forall { case (a, b) => a.vel == b.vel && a.masa == b.masa })
  }

  test("pasoSecuencial: no modifica el arreglo original") {
    val antes = grande.map(_.pos)
    val despues = pasoSecuencial(grande, 2.0)
    assert(grande.map(_.pos).sameElements(antes) && !(despues eq grande))
  }

  test("pasoSecuencial: dos pasos de dt equivalen a un paso de 2dt") {
    val dosPasos = pasoSecuencial(pasoSecuencial(grande, 0.5), 0.5)
    val unPaso = pasoSecuencial(grande, 1.0)
    assert(dosPasos.zip(unPaso).forall { case (a, b) => cerca(a.pos, b.pos) })
  }

  test("pasoSegmento: solo calcula el tramo pedido") {
    val tramo = pasoSegmento(s, 1.0, 1, 2)
    assert(tramo.length == 1 && tramo.head.pos == Vector2(4.0, 0.0))
  }

  test("pasoSegmento: el tramo vacío da un sistema vacío") {
    assert(pasoSegmento(s, 1.0, 1, 1).isEmpty)
  }
  test("pasoSegmento: un tramo interior coincide con ese mismo tramo del paso completo") {
    val completo = pasoSecuencial(grande, 0.5)
    val tramo = pasoSegmento(grande, 0.5, 10, 20)
    assert(tramo.length == 10 && tramo.sameElements(completo.slice(10, 20)))
  }

  test("pasoSegmento: el tramo que cubre todo el sistema da el paso secuencial") {
    assert(pasoSegmento(grande, 0.5, 0, grande.length).sameElements(pasoSecuencial(grande, 0.5)))
  }

  test("pasoSegmento: la longitud del resultado es hasta menos desde") {
    val tramos = List((0, 1), (5, 17), (990, 1000), (0, 1000))
    assert(tramos.forall { case (d, h) => pasoSegmento(grande, 0.5, d, h).length == h - d })
  }

  test("pasoSegmento: dos tramos contiguos pegados reconstruyen el sistema completo") {
    val unido = pasoSegmento(grande, 0.5, 0, 400) ++ pasoSegmento(grande, 0.5, 400, 1000)
    assert(unido.sameElements(pasoSecuencial(grande, 0.5)))
  }

  test("pasoSegmento: respeta el orden y el intervalo semiabierto [desde, hasta)") {
    val cinco: Sistema = Array.tabulate(5)(i =>
      Particula(Vector2(i, i), Vector2(1, 0), 1.0 + i))
    val tramo = pasoSegmento(cinco, 2.0, 1, 4)
    assert(tramo.map(_.pos).sameElements(Array(Vector2(3, 1), Vector2(4, 2), Vector2(5, 3))) &&
      tramo.map(_.masa).sameElements(Array(2.0, 3.0, 4.0)))
  }


  test("pasoParalelo: da lo mismo que el secuencial en un caso simple") {
    assert(pasoParalelo(Array(p), 2.0, 1).head.pos == Vector2(2.0, 4.0))
  }

  test("pasoParalelo: coincide con el secuencial para varios umbrales") {
    val umbrales = List(1, 2, 7, 64, 1000, 5000)
    assert(umbrales.forall { u =>
      pasoParalelo(grande, 0.5, u).zip(pasoSecuencial(grande, 0.5))
        .forall { case (a, b) => cerca(a.pos, b.pos) }
    })
  }

  test("pasoParalelo: conserva el orden y el tamaño del sistema") {
    assert(pasoParalelo(grande, 0.5, 32).length == grande.length)
  }

  test("pasoParalelo: con umbral cero no se queda sin fondo") {
    assert(pasoParalelo(Array(p), 1.0, 0).length == 1)
  }
  test("pasoParalelo: con tamaño impar y umbral pequeño no pierde ni repite partículas") {
    val impar: Sistema = Array.tabulate(37)(i =>
      Particula(Vector2(i, 0), Vector2(1, 1), i + 1.0))
    val despues = pasoParalelo(impar, 1.0, 3)
    val posiciones = Array.tabulate(37)(i => Vector2(i + 1.0, 1.0))
    assert(despues.length == 37 &&
      despues.map(_.masa).sameElements(impar.map(_.masa)) &&
      despues.map(_.pos).sameElements(posiciones))
  }

  test("pasoParalelo: da exactamente lo mismo que el secuencial, incluido umbral igual al tamaño") {
    val umbrales = List(1, 2, 5, 999, 1000, 4000)
    val esperado = pasoSecuencial(grande, 0.25)
    assert(umbrales.forall(u => pasoParalelo(grande, 0.25, u).sameElements(esperado)))
  }

  test("pasoParalelo: un umbral cero o negativo no se cuelga y da el mismo resultado") {
    val pequeno: Sistema = grande.take(50)
    val esperado = pasoSecuencial(pequeno, 0.5)
    assert(List(0, -1, -100).forall(u => pasoParalelo(pequeno, 0.5, u).sameElements(esperado)))
  }

  test("pasoParalelo: el sistema vacío da un sistema vacío con cualquier umbral") {
    assert(List(0, 1, 10).forall(u => pasoParalelo(vacio, 1.0, u).isEmpty))
  }

  test("pasoParalelo: conserva el orden de las partículas y sus velocidades y masas") {
    val despues = pasoParalelo(grande, 0.5, 7)
    assert(despues.length == grande.length &&
      despues.zip(grande).forall { case (a, b) => a.vel == b.vel && a.masa == b.masa })
  }

  // Punto 2 -------------------------------------------------------------------

  test("energiaSecuencial: media masa por velocidad al cuadrado") {
    assert(cerca(energiaSecuencial(s), 25.0))
  }

  test("energiaSecuencial: el sistema vacío no tiene energía") {
    assert(energiaSecuencial(vacio) == 0.0)
  }

  test("energiaSecuencial: una partícula quieta no aporta energía") {
    assert(cerca(energiaSecuencial(Array(s(1))), 0.0))
  }

  test("energiaSecuencial: una sola partícula en movimiento vale media masa por v al cuadrado") {
    val sola = Array(Particula(Vector2(1, 1), Vector2(2, 0), 4.0))
    assert(cerca(energiaSecuencial(sola), 0.5 * 4.0 * (2.0 * 2.0)))
  }

  test("energiaSecuencial: es la suma de las energías y no cambia la masa") {
    val unaSola = s.map(p => energiaSecuencial(Array(p)))
    assert(cerca(energiaSecuencial(s), unaSola.sum))
  }

  test("energiaSecuencial: la masa cuenta y al duplicarla se duplica la energía") {
    val base = Particula(Vector2(0, 0), Vector2(3, 4), 1.0)
    val doble = Particula(Vector2(0, 0), Vector2(3, 4), 2.0)
    assert(cerca(energiaSecuencial(Array(doble)), 2.0 * energiaSecuencial(Array(base))))
    assert(cerca(energiaSecuencial(Array(base)), 0.5 * 1.0 * (5.0 * 5.0)))
  }

  test("energiaSecuencial: la velocidad al cuadrado usa la norma, no una componente") {
    val p = Particula(Vector2(0, 0), Vector2(3, 4), 2.0)
    val esperado = 0.5 * 2.0 * ((3.0 * 3.0) + (4.0 * 4.0))
    assert(cerca(energiaSecuencial(Array(p)), esperado))
    assert(!cerca(energiaSecuencial(Array(p)), 0.5 * 2.0 * (3.0 * 3.0)))
  }

  test("energiaSecuencial: las partículas quietas no alteran la energía de las que se mueven") {
    val movil = Particula(Vector2(0, 0), Vector2(0, 6), 1.0)
    val quieta = Particula(Vector2(9, 9), Vector2(0, 0), 10.0)
    assert(cerca(energiaSecuencial(Array(movil, quieta)), energiaSecuencial(Array(movil))))
    assert(cerca(energiaSecuencial(Array(quieta, movil)), energiaSecuencial(Array(movil))))
  }

  test("masaTotal: suma las masas") {
    assert(cerca(masaTotal(s), 4.0))
  }

  test("masaTotal: el sistema vacío no tiene masa") {
    assert(masaTotal(vacio) == 0.0)
  }

  test("masaTotal: una sola partícula devuelve exactamente su masa") {
    assert(masaTotal(Array(Particula(Vector2(5, 5), Vector2(1, 1), 7.5))) == 7.5)
  }

  test("masaTotal: no depende de las posiciones ni de las velocidades") {
    val quieta = Particula(Vector2(0, 0), Vector2(0, 0), 3.0)
    val rapida = Particula(Vector2(-100, 40), Vector2(9, -9), 3.0)
    assert(cerca(masaTotal(Array(quieta, rapida)), 6.0))
    assert(cerca(masaTotal(Array(rapida, quieta)), 6.0))
  }

  test("masaTotal: es la suma de los pesos uno por uno") {
    val piezas = Array(0.5, 1.25, 2.0, 0.25)
    val sistema = piezas.map(m => Particula(Vector2(0, 0), Vector2(0, 0), m))
    assert(cerca(masaTotal(sistema), piezas.sum))
  }

  test("masaTotal: sumar la masa del sistema es sumar la masa de sus partes, en cualquier orden") {
    val izquierda = Array(Particula(Vector2(0, 0), Vector2(0, 0), 1.5))
    val derecha = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 2.5),
      Particula(Vector2(0, 0), Vector2(0, 0), 4.0))
    assert(cerca(masaTotal(izquierda ++ derecha), masaTotal(izquierda) + masaTotal(derecha)))
    assert(cerca(masaTotal(derecha ++ izquierda), masaTotal(izquierda) + masaTotal(derecha)))
  }

  test("masaTotal: coincide con la masa de la partícula pesada del sistema grande") {
    assert(cerca(masaTotal(grande), grande.map(_.masa).sum))
  }

  test("centroDeMasaSecuencial: el punto medio de dos masas iguales") {
    assert(cerca(centroDeMasaSecuencial(s), Vector2(2.0, 0.0)))
  }

  test("centroDeMasaSecuencial: sin masa el centro es el origen") {
    assert(centroDeMasaSecuencial(vacio) == Vector2(0, 0))
  }

  test("centroDeMasaSecuencial: la masa mayor arrastra el centro") {
    val pesado: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 3.0),
      Particula(Vector2(4, 0), Vector2(0, 0), 1.0))
    assert(cerca(centroDeMasaSecuencial(pesado), Vector2(1.0, 0.0)))
  }

  test("centroDeMasaSecuencial: una sola partícula es su propia posición") {
    val sola = Array(Particula(Vector2(2.5, -7), Vector2(3, 3), 9.0))
    assert(cerca(centroDeMasaSecuencial(sola), Vector2(2.5, -7)))
  }

  test("centroDeMasaSecuencial: con masas iguales es el promedio de las posiciones") {
    val sistema = Array(
      Particula(Vector2(-2, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(1, 3), Vector2(5, 5), 1.0),
      Particula(Vector2(4, 6), Vector2(0, 0), 1.0))
    assert(cerca(centroDeMasaSecuencial(sistema), Vector2(1.0, 3.0)))
  }

  test("centroDeMasaSecuencial: usa las dos componentes y no solo la primera") {
    val sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(0, 8), Vector2(0, 0), 3.0))
    assert(cerca(centroDeMasaSecuencial(sistema), Vector2(0.0, 6.0)))
    assert(!cerca(centroDeMasaSecuencial(sistema), Vector2(0.0, 4.0)))
  }

  test("centroDeMasaSecuencial: no depende del orden de las partículas") {
    val a = Particula(Vector2(0, 0), Vector2(0, 0), 1.0)
    val b = Particula(Vector2(6, 0), Vector2(0, 0), 2.0)
    val c = Particula(Vector2(3, 9), Vector2(1, 1), 5.0)
    val esperado = centroDeMasaSecuencial(Array(a, b, c))
    assert(cerca(centroDeMasaSecuencial(Array(c, b, a)), esperado))
    assert(cerca(centroDeMasaSecuencial(Array(b, a, c)), esperado))
  }

  test("centroDeMasaSecuencial: el centro está entre las posiciones y se queda quieto al repetir una partícula") {
    val sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(4, 0), Vector2(0, 0), 1.0))
    val centro = centroDeMasaSecuencial(sistema)
    assert(centro.x >= 0.0 && centro.x <= 4.0)
    assert(cerca(centroDeMasaSecuencial(Array(sistema(0), sistema(0), sistema(1))), Vector2(4.0 / 3.0, 0.0)))
  }

  test("centroDeMasaSecuencial: coincide con la fórmula explícita del sistema grande") {
    val masa = grande.map(_.masa).sum
    val suma = grande.foldLeft(Vector2(0, 0))((acc, p) => acc + p.pos * p.masa)
    assert(cerca(centroDeMasaSecuencial(grande), suma * (1.0 / masa)))
  }

  // Punto 3 -------------------------------------------------------------------

  test("energiaParalela: coincide con la secuencial en el ejemplo") {
    assert(cerca(energiaParalela(s, 1), 25.0))
  }

  test("energiaParalela: coincide con la secuencial para varios umbrales") {
    val esperado = energiaSecuencial(grande)
    assert(List(1, 3, 64, 999, 5000).forall(u => cerca(energiaParalela(grande, u), esperado)))
  }

  test("energiaParalela: sobre el sistema vacío da cero") {
    assert(energiaParalela(vacio, 1) == 0.0)
  }

  test("centroDeMasaParalelo: coincide con el secuencial en el ejemplo") {
    assert(cerca(centroDeMasaParalelo(s, 1), Vector2(2.0, 0.0)))
  }

  test("centroDeMasaParalelo: coincide con el secuencial para varios umbrales") {
    val esperado = centroDeMasaSecuencial(grande)
    assert(List(1, 3, 64, 999, 5000).forall(u => cerca(centroDeMasaParalelo(grande, u), esperado)))
  }

  test("centroDeMasaParalelo: promediar promedios daría otra cosa") {
    // Tres partículas: dos en el origen y una lejos. El promedio de los
    // promedios de las dos mitades no es el centro de masa del conjunto.
    val tres: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(9, 0), Vector2(0, 0), 1.0))
    assert(cerca(centroDeMasaParalelo(tres, 1), Vector2(3.0, 0.0)))
  }

  test("centroDeMasaParalelo: sin masa el centro es el origen") {
    assert(centroDeMasaParalelo(vacio, 1) == Vector2(0, 0))
  }
  test("energiaParalela: tres partículas a mano suman 31 con cualquier umbral") {
    val tres: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(3, 4), 2.0),  // 0.5 * 2 * 25 = 25
      Particula(Vector2(1, 1), Vector2(0, 2), 1.0),  // 0.5 * 1 * 4  = 2
      Particula(Vector2(2, 2), Vector2(1, 1), 4.0))  // 0.5 * 4 * 2  = 4
    assert(List(1, 2, 3, 10).forall(u => cerca(energiaParalela(tres, u), 31.0)))
  }

  test("energiaParalela: con tamaño impar y umbral pequeño no pierde ni repite partículas") {
    val impar: Sistema = Array.tabulate(37)(i =>
      Particula(Vector2(i, 0), Vector2(i, 1), i + 1.0))
    assert(cerca(energiaParalela(impar, 3), energiaSecuencial(impar)))
  }

  test("energiaParalela: las partículas quietas no alteran la energía de las que se mueven") {
    val movil = Particula(Vector2(0, 0), Vector2(0, 6), 1.0)   // 0.5 * 1 * 36 = 18
    val quieta = Particula(Vector2(9, 9), Vector2(0, 0), 10.0)
    assert(cerca(energiaParalela(Array(movil, quieta, quieta, quieta), 1), 18.0))
    assert(cerca(energiaParalela(Array(quieta, quieta, movil, quieta), 1), 18.0))
  }

  test("energiaParalela: un umbral cero o negativo no se cuelga y da el mismo resultado") {
    val pequeno: Sistema = grande.take(50)
    val esperado = energiaSecuencial(pequeno)
    assert(List(0, -1, -100).forall(u => cerca(energiaParalela(pequeno, u), esperado)))
  }

  test("energiaParalela: no depende del orden de las partículas") {
    val esperado = energiaSecuencial(grande)
    assert(cerca(energiaParalela(grande.reverse, 16), esperado))
    assert(cerca(energiaParalela(grande.reverse, grande.length), esperado))
  }
  test("centroDeMasaParalelo: cuatro masas distintas a mano dan (3, 4) con cualquier umbral") {
    val cuatro: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(4, 0), Vector2(0, 0), 3.0),
      Particula(Vector2(0, 8), Vector2(0, 0), 1.0),
      Particula(Vector2(4, 8), Vector2(0, 0), 3.0))
    assert(List(1, 2, 4, 10).forall(u => cerca(centroDeMasaParalelo(cuatro, u), Vector2(3.0, 4.0))))
  }

  test("centroDeMasaParalelo: una sola partícula es su propia posición") {
    val sola = Array(Particula(Vector2(2.5, -7), Vector2(3, 3), 9.0))
    assert(cerca(centroDeMasaParalelo(sola, 1), Vector2(2.5, -7)))
  }

  test("centroDeMasaParalelo: una partícula muy pesada domina el centro") {
    val pesada: Sistema = Array(
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(0, 0), Vector2(0, 0), 1.0),
      Particula(Vector2(10, 0), Vector2(0, 0), 97.0))
    assert(List(1, 2, 4).forall(u => cerca(centroDeMasaParalelo(pesada, u), Vector2(9.7, 0.0))))
  }

  test("centroDeMasaParalelo: si todas las masas son cero el centro es el origen") {
    val sinMasa: Sistema = Array.tabulate(8)(i =>
      Particula(Vector2(i, i), Vector2(1, 1), 0.0))
    assert(centroDeMasaParalelo(sinMasa, 1) == Vector2(0, 0))
  }

  test("centroDeMasaParalelo: no depende del orden de las partículas ni del umbral") {
    val esperado = centroDeMasaSecuencial(grande)
    assert(cerca(centroDeMasaParalelo(grande.reverse, 16), esperado))
    assert(List(0, -1, 37).forall(u => cerca(centroDeMasaParalelo(grande, u), esperado)))
  }

  // Punto 4 -------------------------------------------------------------------

  test("trabajo: una operación por partícula") { assert(trabajo(1024) == 1024) }

  test("profundidad: si el umbral cubre todo, no se parte") {
    assert(profundidad(1024, 1024) == 1024)
  }

  test("profundidad: una partición y el tramo que queda") {
    assert(profundidad(1024, 512) == 513)
  }

  test("profundidad: diez particiones y un elemento") {
    assert(profundidad(1024, 1) == 11)
  }

  test("tiempoEstimado: profundidad más trabajo entre procesadores") {
    assert(cerca(tiempoEstimado(1024, 4, 1), 267.0))
  }

  test("tiempoEstimado: con un procesador no hay ganancia") {
    assert(cerca(tiempoEstimado(1024, 1, 1), 11.0 + 1024.0))
  }

  test("profundidad: nunca supera el trabajo") {
    val casos = for (n <- List(1, 7, 64, 1024); u <- List(1, 8, 64)) yield (n, u)
    assert(casos.forall { case (n, u) => profundidad(n, u) <= trabajo(n) })
  }

  // Punto 5 -------------------------------------------------------------------

  test("aceleracion: cuatro veces más rápido da 4.0") {
    assert(cerca(aceleracion(100.0, 25.0), 4.0))
  }

  test("aceleracion: el mismo tiempo da 1.0") {
    assert(cerca(aceleracion(100.0, 100.0), 1.0))
  }

  test("umbralConveniente: sin candidatos devuelve el tamaño del sistema") {
    assert(umbralConveniente(grande, 0.5, Nil) == grande.length)
  }

  test("umbralConveniente: devuelve uno de los candidatos") {
    val candidatos = List(16, 128, 1024)
    assert(candidatos.contains(umbralConveniente(grande, 0.5, candidatos)))
  }
}
