package taller

/**
 * El punto 5 mide aquí, sobre sistemas de al menos un millón de partículas,
 * el tiempo de `pasoSecuencial` y el de `pasoParalelo` con varios umbrales, y
 * reporta la aceleración de cada uno. La fila con umbral igual al tamaño del
 * sistema es la de control: no reparte nada y su aceleración debe ser 1.
 */
object App {

  case class Fila(n: Int, umbral: Int, secuencial: Double, paralelo: Double, aceleracion: Double)

  def main(args: Array[String]): Unit = {
    val sim = new SimulacionParticulas()
    import sim._

    val dt = 0.01
    val tamanos = List(1000000, 2000000, 4000000)
    val divisores = List(8, 64, 512, 4096)

    /** Un sistema grande y parejo, con datos que no dependen de dónde se corte. */
    def sistemaDe(n: Int): Sistema =
      Array.tabulate(n) { i =>
        Particula(
          Vector2((i % 1000).toDouble, ((i / 1000) % 1000).toDouble),
          Vector2(1.0 + (i % 7), -1.0 - (i % 5)),
          1.0 + (i % 10))
      }

    def dosDecimales(x: Double): String = f"$x%.2f"

    def textoFila(f: Fila): String = {
      val etiqueta = if (f.umbral == f.n) s"${f.umbral} (control)" else f.umbral.toString
      s"| ${f.n} | $etiqueta | ${dosDecimales(f.secuencial)} | ${dosDecimales(f.paralelo)} | ${dosDecimales(f.aceleracion)} |"
    }

    println(s"Núcleos disponibles: ${Runtime.getRuntime.availableProcessors}")
    println(s"dt = $dt; cada tiempo es la mediana de 3 mediciones de ScalaMeter, en ms")
    println()
    println("| n | Umbral | Secuencial (ms) | Paralelo (ms) | Aceleración |")
    println("|---:|---:|---:|---:|---:|")

    val filas = tamanos.flatMap { n =>
      val s = sistemaDe(n)
      val secuencial = medir(3)(pasoSecuencial(s, dt))
      val umbrales = n :: divisores.map(d => Math.max(n / d, 1))
      umbrales.map { umbral =>
        val paralelo = medir(3)(pasoParalelo(s, dt, umbral))
        Fila(n, umbral, secuencial, paralelo, aceleracion(secuencial, paralelo))
      }
    }

    filas.foreach(f => println(textoFila(f)))

    println()
    tamanos.foreach { n =>
      val mejor = filas.filter(_.n == n).maxBy(_.aceleracion)
      println(s"Mejor umbral medido para n = $n: ${mejor.umbral} (aceleración ${dosDecimales(mejor.aceleracion)})")
    }
  }
}
