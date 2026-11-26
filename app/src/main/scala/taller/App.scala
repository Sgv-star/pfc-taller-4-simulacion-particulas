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
    val tamanos = List(1000000, 1500000, 2000000)
    val divisores = List(8, 64, 512)
    val repeticiones = 7

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

    /**
     * Mide las dos versiones pegadas, para que el reloj y la memoria estén en
     * las mismas condiciones, y se queda con el par cuya aceleración es la
     * mediana. Así una pausa del recolector no decide el resultado.
     */
    def medirFila(n: Int, umbral: Int, s: Sistema): Fila = {
      val muestras = Vector.fill(repeticiones) {
        val secuencial = medir(1)(pasoSecuencial(s, dt))
        val paralelo = medir(1)(pasoParalelo(s, dt, umbral))
        Fila(n, umbral, secuencial, paralelo, aceleracion(secuencial, paralelo))
      }
      val ordenadas = muestras.sortBy(_.aceleracion)
      ordenadas.apply(ordenadas.length / 2)
    }

    // El compilador interno necesita ver las dos rutas compiladas antes de la
    // primera medida; si no, la primera fila paga la compilación y el control
    // no compara lo mismo.
    val calentamiento = sistemaDe(200000)
    pasoSecuencial(calentamiento, dt)
    List(200000, 10000, 500).foreach(u => pasoParalelo(calentamiento, dt, u))

    println(s"Núcleos disponibles: ${Runtime.getRuntime.availableProcessors}")
    println(s"dt = $dt; cada fila es la mediana de $repeticiones pares de mediciones de ScalaMeter, en ms")
    println()
    println("| n | Umbral | Secuencial (ms) | Paralelo (ms) | Aceleración |")
    println("|---:|---:|---:|---:|---:|")

    val filas = tamanos.flatMap { n =>
      System.gc()
      val s = sistemaDe(n)
      val umbrales = n :: divisores.map(d => n / d)
      umbrales.map(u => medirFila(n, u, s))
    }

    filas.foreach(f => println(textoFila(f)))

    println()
    tamanos.foreach { n =>
      val mejor = filas.filter(_.n == n).maxBy(_.aceleracion)
      println(s"Mejor umbral medido para n = $n: ${mejor.umbral} (aceleración ${dosDecimales(mejor.aceleracion)})")
    }
  }
}
