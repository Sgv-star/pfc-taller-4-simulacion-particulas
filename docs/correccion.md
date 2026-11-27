# Informe de corrección

**Taller 4 — Programación funcional y concurrente: simulación de partículas en paralelo**

Este informe argumenta, con notación matemática, que cada función de `SimulacionParticulas.scala` hace lo que pide el enunciado. Se demuestra además que partir el sistema en segmentos no pierde ni repite partículas, que las operaciones con que se combinan los resultados parciales son asociativas y que cada versión paralela devuelve lo mismo que su versión secuencial.

## 0. Qué se argumenta y dónde

| Lo que pide el enunciado | Dónde se argumenta |
|---|---|
| Partir el sistema en segmentos no pierde ni repite partículas | Sección 2.4 (Lemas 1 y 2) |
| La operación que combina los resultados parciales es asociativa | Secciones 2.5 (concatenación) y 4.1 (suma de reales y suma de pares) |
| La versión paralela da el mismo resultado que la secuencial | Secciones 2.6, 4.2, 4.3 y el resumen de la sección 7 |
| Por qué la suma sirve para repartir y el promedio no | Sección 4.4 |
| Corrección de cada función de los puntos 1 a 5 | Secciones 2 a 6 |

## 1. Notación y convenciones

- El sistema es un arreglo $`s = (p_0, p_1, \ldots, p_{n-1})`$ con $`n = |s|`$. Cada partícula es $`p_i = (r_i, v_i, m_i)`$, con posición $`r_i \in \mathbb{R}^2`$, velocidad $`v_i \in \mathbb{R}^2`$ y masa $`m_i \in \mathbb{R}`$. Los índices empiezan en $`0`$.
- Un **segmento** $`[d,h)`$, con $`0 \le d \le h \le n`$, contiene los índices $`d, d+1, \ldots, h-1`$. Su tamaño es $`k = h - d`$ y $`s[d,h) = (p_d, \ldots, p_{h-1})`$. El segmento $`[d,d)`$ es vacío.
- $`\Delta t`$ es el parámetro `dt`.
- $`u`$ es el parámetro `umbral` y $`u' = \max(u, 1)`$ es el umbral que realmente compara el código.
- $`\lfloor x \rfloor`$ y $`\lceil x \rceil`$ son el piso y el techo. En Scala, con enteros, `k / 2` es $`\lfloor k/2 \rfloor`$ y `k - k / 2` es $`\lceil k/2 \rceil`$.
- **Sistema de ejemplo.** Salvo que se diga otra cosa, los ejemplos numéricos usan
  el sistema de dos partículas de las pruebas:
  $`p_0 = ((0,0),(3,4),2)`$ y $`p_1 = ((4,0),(0,0),2)`$. `proceso.md` usa además un
  sistema de cuatro partículas como ejemplo didáctico.
- **Sobre `parallel`.** `parallel(a, b)` devuelve el par formado por los valores de `a` y de `b`. Las funciones del taller solo leen `s` y no tienen variables mutables, así que evaluar `a` y `b` en paralelo da el mismo par que evaluarlas una tras otra. Por eso se puede razonar con el modelo de sustitución como en un programa secuencial:

$$
\mathtt{parallel}(a, b) \;\to\; ([\![ a ]\!], [\![ b ]\!])
$$

- **Sobre la aritmética.** Las especificaciones y las demostraciones se hacen en $`\mathbb{R}`$. En `Double` la suma no es exactamente asociativa porque cada operación redondea, y por eso el enunciado pide que las pruebas de energía y centro de masa usen una tolerancia pequeña. `pasoParalelo` no suma valores de distintas partículas: cada posición se calcula con la misma expresión que en la versión secuencial, así que ahí el resultado es exactamente igual.
- Las pruebas por inducción sobre el tamaño $`k`$ de un segmento son por **inducción fuerte**: se supone la propiedad para todo tamaño menor que $`k`$ y se demuestra para $`k`$.

---

## 2. Punto 1: un paso de la simulación

### 2.1 `mover`

**Especificación.** Para $`p = (r, v, m)`$, la partícula movida es

$$
F(p, \Delta t) = (\, r + \Delta t \cdot v, \; v, \; m \,)
$$

**Programa.**

```scala
def mover(p: Particula, dt: Double): Particula = {
  val nueva_pos = p.pos + p.vel * dt
  new Particula(nueva_pos, p.vel, p.masa)
}
```

**Argumento.** No hay recursión; basta sustituir. En `Vector2`, `p.vel * dt` es el producto del vector por el escalar $`\Delta t`$ y `+` suma componente a componente. Entonces `nueva_pos` vale $`r + \Delta t \cdot v`$ y el resultado es $`(r + \Delta t \cdot v, \, v, \, m)`$, que es $`F(p, \Delta t)`$. La velocidad y la masa se copian sin cambio, como pide el enunciado.

**Ejemplo del enunciado.** Con $`p = ((0,0), (1,2), 1)`$ y $`\Delta t = 2`$: $`(0,0) + 2 \cdot (1,2) = (2,4)`$. Con $`\Delta t = 0`$ la posición queda en $`(0,0)`$.

### 2.2 `pasoSegmento`

**Especificación.** Para $`0 \le d \le h \le n`$:

$$
S(s, \Delta t, d, h) = \big( F(p_d, \Delta t), \; F(p_{d+1}, \Delta t), \; \ldots, \; F(p_{h-1}, \Delta t) \big)
$$

**Programa.**

```scala
def pasoSegmento(s: Sistema, dt: Double, desde: Int, hasta: Int): Sistema =
  s.slice(desde, hasta).map(p => mover(p, dt))
```

**Argumento.** Con $`0 \le d \le h \le n`$, `s.slice(d, h)` es el arreglo $`(p_d, \ldots, p_{h-1})`$, es decir, justo el segmento $`[d,h)`$. `map` aplica `mover` a cada elemento, conserva el orden y conserva el tamaño. Por lo tanto el resultado es $`S(s, \Delta t, d, h)`$ y tiene $`h - d`$ elementos. Si $`d = h`$ devuelve el arreglo vacío.

De la definición de $`S`$ se sigue directamente que, para $`d \le m \le h`$, el resultado de un segmento es la concatenación de los resultados de sus dos partes:

$$
S(s, \Delta t, d, h) = S(s, \Delta t, d, m) \mathbin{+\!\!+} S(s, \Delta t, m, h)
$$

### 2.3 `pasoSecuencial`

**Programa.**

```scala
def pasoSecuencial(s: Sistema, dt: Double): Sistema =
  pasoSegmento(s, dt, 0, s.length)
```

**Argumento.** Es `pasoSegmento` con $`d = 0`$ y $`h = n`$, que cumple $`0 \le 0 \le n \le n`$. Por la sección 2.2:

$$
\mathtt{pasoSecuencial}(s, \Delta t) = S(s, \Delta t, 0, n) = \big( F(p_0, \Delta t), \ldots, F(p_{n-1}, \Delta t) \big)
$$

Es decir, cada partícula aparece una vez, movida, en su misma posición del arreglo.

### 2.4 La partición no pierde ni repite partículas

`pasoParalelo` y las funciones paralelas del punto 3 parten un segmento $`[d,h)`$ con la misma regla: si $`k = h - d > u'`$, el punto de corte es

$$
m = d + \lfloor k/2 \rfloor
$$

y los dos segmentos hijos son $`[d, m)`$ y $`[m, h)`$.

**Lema 1 (un corte).** Si $`k = h - d > u' \ge 1`$, entonces $`k \ge 2`$ y se cumple:

1. $`d < m < h`$.
2. $`[d,m) \cap [m,h) = \varnothing`$ (ninguna partícula se repite).
3. $`[d,m) \cup [m,h) = [d,h)`$ (ninguna partícula se pierde).
4. Los tamaños de los hijos son $`\lfloor k/2 \rfloor`$ y $`\lceil k/2 \rceil`$, y ambos están entre $`1`$ y $`k-1`$.

*Demostración.*

1. Como $`k \ge 2`$, $`\lfloor k/2 \rfloor \ge 1`$, luego $`m \ge d + 1 > d`$. Además $`\lfloor k/2 \rfloor \le k/2 < k`$, luego $`m < h`$.
2. Un índice $`i`$ está en $`[d,m)`$ si $`i < m`$ y en $`[m,h)`$ si $`i \ge m`$; no puede cumplir ambas.
3. Todo $`i`$ con $`d \le i < h`$ cumple $`i < m`$ o $`i \ge m`$, así que está en alguno de los dos hijos. Y todo índice de un hijo está en $`[d,h)`$ porque $`d < m < h`$.
4. $`m - d = \lfloor k/2 \rfloor \ge 1`$ y $`h - m = k - \lfloor k/2 \rfloor = \lceil k/2 \rceil`$. Para $`k \ge 2`$, $`\lceil k/2 \rceil \le k - 1`$. $`\square`$

**Lema 2 (toda la recursión).** Sea $`\mathtt{resolver}(d,h)`$ cualquiera de las funciones recursivas que parten con la regla anterior y se detienen cuando $`k \le u'`$. Las hojas de la recursión de $`\mathtt{resolver}(d,h)`$ son segmentos consecutivos $`[a_1,b_1), [a_2,b_2), \ldots, [a_j,b_j)`$ con $`a_1 = d`$, $`b_j = h`$ y $`b_i = a_{i+1}`$. En consecuencia, cada índice de $`[d,h)`$ pertenece a exactamente una hoja.

*Demostración.* Por inducción fuerte sobre $`k = h-d`$.

- Si $`k \le u'`$, no se parte: hay una sola hoja, $`[d,h)`$, y la propiedad es inmediata. (Esto incluye $`k = 0`$.)
- Si $`k > u'`$, por el Lema 1 se parte en $`[d,m)`$ y $`[m,h)`$, ambos de tamaño menor que $`k`$. Por hipótesis de inducción, las hojas del hijo izquierdo son consecutivas de $`d`$ a $`m`$ y las del derecho, de $`m`$ a $`h`$. Al ponerlas una detrás de otra, la última del izquierdo termina en $`m`$ y la primera del derecho empieza en $`m`$, así que la cadena completa va de $`d`$ a $`h`$ sin huecos ni solapes. $`\square`$

### 2.5 `pasoParalelo`: terminación y corrección

**Programa.**

```scala
def pasoParalelo(s: Sistema, dt: Double, umbral: Int): Sistema = {
  def resolver(desde: Int, hasta: Int): Sistema =
    if (hasta - desde > Math.max(umbral, 1)) {
      val mitad = desde + (hasta - desde) / 2
      val (izq, der) = parallel(resolver(desde, mitad), resolver(mitad, hasta))
      izq ++ der
    } else pasoSegmento(s, dt, desde, hasta)

  resolver(0, s.length)
}
```

**Terminación.** Cada llamada recursiva se hace sobre un segmento de tamaño estrictamente menor que $`k`$ (Lema 1, punto 4), y los tamaños son enteros no negativos. Como la recursión solo ocurre cuando $`k > u' \ge 1`$, el corte $`m`$ queda estrictamente entre $`d`$ y $`h`$ y los dos hijos tienen al menos un elemento. Por eso la recursión termina para cualquier valor de `umbral`, incluso cero o negativo.

**Teorema 1.** Para todo $`0 \le d \le h \le n`$:

$$
\mathtt{resolver}(d,h) = S(s, \Delta t, d, h)
$$

*Demostración.* Inducción fuerte sobre $`k = h-d`$.

- **Caso $`k \le u'`$** (incluye $`k = 0`$). El `if` toma la rama `else` y el programa devuelve `pasoSegmento(s, dt, d, h)`, que por la sección 2.2 es $`S(s, \Delta t, d, h)`$.
- **Caso $`k > u'`$.** Por el Lema 1, $`d < m < h`$ y los dos hijos son más pequeños que $`k`$. Por hipótesis de inducción, $`\mathtt{resolver}(d,m) = S(s, \Delta t, d, m)`$ y $`\mathtt{resolver}(m,h) = S(s, \Delta t, m, h)`$. Por la convención sobre `parallel`, `(izq, der)` son esos dos valores. El resultado es

$$
\mathtt{izq} \mathbin{+\!\!+} \mathtt{der} = S(s, \Delta t, d, m) \mathbin{+\!\!+} S(s, \Delta t, m, h) = S(s, \Delta t, d, h)
$$

donde la última igualdad es la de la sección 2.2. $`\square`$

**La concatenación es asociativa.** La operación con que `pasoParalelo` combina los resultados parciales es `++`. Para arreglos $`x, y, z`$:

$$
(x \mathbin{+\!\!+} y) \mathbin{+\!\!+} z = x \mathbin{+\!\!+} (y \mathbin{+\!\!+} z)
$$

porque ambos lados son el arreglo que tiene los elementos de $`x`$, luego los de $`y`$ y luego los de $`z`$, en ese orden. El arreglo vacío es el elemento neutro: $`x \mathbin{+\!\!+} () = () \mathbin{+\!\!+} x = x`$. Gracias a esto, la forma del árbol de llamadas (qué mitades se concatenan primero) no cambia el resultado. La concatenación **no** es conmutativa, pero el orden está garantizado porque el código siempre pone `izq` antes que `der`, y `izq` cubre índices menores que `der`.

### 2.6 `pasoParalelo` y `pasoSecuencial` devuelven lo mismo

Tomando $`d = 0`$ y $`h = n`$ en el Teorema 1 y usando la sección 2.3:

$$
\forall s, \Delta t, u : \quad \mathtt{pasoParalelo}(s, \Delta t, u) = S(s, \Delta t, 0, n) = \mathtt{pasoSecuencial}(s, \Delta t)
$$

La igualdad es exacta: cada posición se calcula con la misma función `mover` sobre la misma partícula. Lo único que cambia con $`u`$ es cuántas hojas hay y, por lo tanto, cuánto tarda, no qué se devuelve.

**Ejemplo del enunciado.** `pasoParalelo(Array(p), 2.0, 1)` tiene $`n = 1 \le u' = 1`$, es una sola hoja, y devuelve $`(2,4)`$ igual que `pasoSecuencial`.

---

## 3. Punto 2: magnitudes globales en secuencial

Para las tres funciones se usa el mismo método de la recursión de cola: se describe el **estado** $`(i, \ldots)`$, una **invariante** que se cumple en todo momento y la condición de parada.

### 3.1 `masaTotal`

**Especificación.**

$$
M(s) = \sum_{j=0}^{n-1} m_j
$$

**Programa.**

```scala
def masaTotal(s: Sistema): Double = {
  @annotation.tailrec
  def aux(i: Int, acc: Double): Double =
    if (i >= s.length) acc
    else aux(i + 1, acc + s(i).masa)

  aux(0, 0.0)
}
```

**Argumento.**

- Estado: $`(i, acc)`$. Estado inicial: $`(0, 0)`$. Es final cuando $`i \ge n`$.
- Invariante:

$$
\mathrm{Inv}(i, acc) \equiv 0 \le i \le n \;\wedge\; acc = \sum_{j=0}^{i-1} m_j
$$

- **Inicio.** $`\mathrm{Inv}(0, 0)`$ se cumple: $`0 \le 0 \le n`$ y la suma vacía vale $`0`$.
- **Se conserva.** Si $`\mathrm{Inv}(i, acc)`$ y $`i < n`$, el nuevo estado es $`(i+1, \, acc + m_i)`$. Se cumple $`i + 1 \le n`$ y $`acc + m_i = \sum_{j=0}^{i-1} m_j + m_i = \sum_{j=0}^{i} m_j`$.
- **Al final.** Si $`i \ge n`$ y $`i \le n`$ entonces $`i = n`$, y $`acc = \sum_{j=0}^{n-1} m_j = M(s)`$.
- **Termina.** $`n - i`$ es un entero no negativo que baja en $`1`$ en cada llamada.

Con $`s`$ vacío devuelve $`0`$. Con el sistema de ejemplo de dos partículas, $`2 + 2 = 4`$.

### 3.2 `energiaSecuencial`

**Especificación.** La energía cinética de la partícula $`j`$ es $`e_j = \tfrac{1}{2}\, m_j \lVert v_j \rVert^2`$ y la del sistema es

$$
E(s) = \sum_{j=0}^{n-1} e_j
$$

**Programa.**

```scala
def energiaSecuencial(s: Sistema): Double = {
  def energiaDe(p: Particula): Double = (0.5 * p.masa) * (p.vel.norma * p.vel.norma)
  @annotation.tailrec
  def aux(i: Int, acc: Double): Double =
    if (i >= s.length) acc
    else aux(i + 1, acc + energiaDe(s(i)))

  aux(0, 0.0)
}
```

**Argumento.** `norma` es $`\sqrt{x^2 + y^2} = \lVert v \rVert`$, así que `p.vel.norma * p.vel.norma` es $`\lVert v \rVert^2`$ y `energiaDe(p)` es $`\tfrac{1}{2}\, m \lVert v \rVert^2 = e`$ para esa partícula. La invariante es

$$
\mathrm{Inv}(i, acc) \equiv 0 \le i \le n \;\wedge\; acc = \sum_{j=0}^{i-1} e_j
$$

- **Inicio.** $`\mathrm{Inv}(0, 0)`$: la suma vacía es $`0`$.
- **Se conserva.** De $`(i, acc)`$ con $`i < n`$ se pasa a $`(i+1, \, acc + e_i)`$ y $`acc + e_i = \sum_{j=0}^{i} e_j`$.
- **Al final.** $`i = n`$ y $`acc = E(s)`$.
- **Termina.** $`n - i`$ baja en $`1`$ en cada llamada.

**Ejemplo del enunciado.** $`e_0 = \tfrac{1}{2} \cdot 2 \cdot 5^2 = 25`$, $`e_1 = 0`$, luego $`E(s) = 25`$. Con el arreglo vacío devuelve $`0`$.

### 3.3 `centroDeMasaSecuencial`

**Especificación.** Sea $`M = M(s)`$.

$$
C(s) = \begin{cases} \dfrac{1}{M} \displaystyle\sum_{j=0}^{n-1} m_j\, r_j & \text{si } M \ne 0 \\[2ex] (0,0) & \text{si } M = 0 \end{cases}
$$

**Programa.**

```scala
def centroDeMasaSecuencial(s: Sistema): Vector2 = {
  @annotation.tailrec
  def aux(i: Int, suma: Vector2, masa: Double): (Vector2, Double) =
    if (i >= s.length) (suma, masa)
    else {
      val p = s(i)
      aux(i + 1, suma + p.pos * p.masa, masa + p.masa)
    }

  val (suma, masa) = aux(0, Vector2(0, 0), 0.0)
  if (masa == 0.0) Vector2(0, 0) else suma * (1.0 / masa)
}
```

**Argumento.** El estado es $`(i, \Sigma, \mu)`$ con $`\Sigma \in \mathbb{R}^2`$ la suma ponderada y $`\mu`$ la masa acumulada. La invariante es

$$
\mathrm{Inv}(i, \Sigma, \mu) \equiv 0 \le i \le n \;\wedge\; \Sigma = \sum_{j=0}^{i-1} m_j\, r_j \;\wedge\; \mu = \sum_{j=0}^{i-1} m_j
$$

- **Inicio.** $`(0, (0,0), 0)`$ cumple la invariante: ambas sumas son vacías.
- **Se conserva.** `p.pos * p.masa` es $`m_i\, r_i`$. El nuevo estado es $`(i+1, \, \Sigma + m_i r_i, \, \mu + m_i)`$, que cumple la invariante para $`i+1`$.
- **Al final.** $`i = n`$, así que `aux` devuelve $`\big( \sum_{j} m_j r_j, \; \sum_{j} m_j \big) = (\Sigma_s, M)`$.
- **Termina.** $`n - i`$ baja en $`1`$ en cada llamada.

Después, el programa divide **una sola vez**: si `masa == 0.0` devuelve el origen y, si no, `suma * (1.0 / masa)` es $`\tfrac{1}{M}\Sigma_s`$. Esto es exactamente $`C(s)`$.

**Ejemplo del enunciado.** $`\Sigma_s = 2 \cdot (0,0) + 2 \cdot (4,0) = (8,0)`$ y $`M = 4`$, luego $`C(s) = (2,0)`$.

---

## 4. Punto 3: magnitudes globales en paralelo

### 4.1 Las operaciones de combinación son asociativas

Las dos funciones paralelas combinan resultados parciales con una de estas operaciones:

- La suma de reales $`+`$ sobre $`\mathbb{R}`$, con neutro $`0`$ (energía).
- La suma de pares $`\oplus`$ sobre $`\mathbb{R}^2 \times \mathbb{R}`$, con neutro $`((0,0), 0)`$ (centro de masa):

$$
(a, \alpha) \oplus (b, \beta) = (a + b, \; \alpha + \beta)
$$

**Lema 3.** $`(\mathbb{R}, +, 0)`$ y $`(\mathbb{R}^2 \times \mathbb{R}, \oplus, ((0,0),0))`$ son asociativas y tienen elemento neutro.

*Demostración.* Para $`+`$ en $`\mathbb{R}`$ es la propiedad conocida de los reales. Para $`\oplus`$, como la suma en $`\mathbb{R}^2`$ es componente a componente, también es asociativa, y se tiene:

$$
\begin{aligned}
((a,\alpha) \oplus (b,\beta)) \oplus (c,\gamma) &= ((a+b)+c, \; (\alpha+\beta)+\gamma) \\
&= (a+(b+c), \; \alpha+(\beta+\gamma)) \\
&= (a,\alpha) \oplus ((b,\beta) \oplus (c,\gamma))
\end{aligned}
$$

El neutro cumple $`(a,\alpha) \oplus ((0,0),0) = (a,\alpha)`$. $`\square`$

Además, para segmentos disjuntos que cubren a $`[d,h)`$ (Lemas 1 y 2), una suma sobre $`[d,h)`$ es la suma de las sumas sobre las partes. Para $`d \le m \le h`$:

$$
\sum_{j=d}^{h-1} x_j = \sum_{j=d}^{m-1} x_j + \sum_{j=m}^{h-1} x_j
$$

Esta igualdad es la que usan las dos demostraciones siguientes. Se escribe $`E[d,h) = \sum_{j=d}^{h-1} e_j`$, $`\Sigma[d,h) = \sum_{j=d}^{h-1} m_j r_j`$ y $`M[d,h) = \sum_{j=d}^{h-1} m_j`$, todas iguales a $`0`$ cuando el segmento es vacío.

### 4.2 `energiaParalela`

**Programa.**

```scala
def energiaParalela(s: Sistema, umbral: Int): Double = {
  def energiaDe(p: Particula): Double = (0.5 * p.masa) * (p.vel.norma * p.vel.norma)

  @tailrec
  def sumaTramo(i: Int, hasta: Int, acc: Double): Double =
    if (i >= hasta) acc
    else sumaTramo(i + 1, hasta, acc + energiaDe(s(i)))

  def resolver(desde: Int, hasta: Int): Double =
    if (hasta - desde > Math.max(umbral, 1)) {
      val mitad = desde + (hasta - desde) / 2
      val (izq, der) = parallel(resolver(desde, mitad), resolver(mitad, hasta))
      izq + der
    } else sumaTramo(desde, hasta, 0.0)

  resolver(0, s.length)
}
```

**Lema 4 (`sumaTramo`).** Para $`0 \le i`$ y $`h \le n`$: $`\mathtt{sumaTramo}(i, h, acc) = acc + \sum_{j=i}^{h-1} e_j`$, donde la suma es vacía si $`i \ge h`$.

*Demostración.* Inducción sobre $`\max(h - i, 0)`$. Si $`i \ge h`$, devuelve $`acc`$, que es $`acc + 0`$. Si $`i < h`$, el programa llama a $`\mathtt{sumaTramo}(i+1, h, acc + e_i)`$, que por hipótesis de inducción vale $`(acc + e_i) + \sum_{j=i+1}^{h-1} e_j = acc + \sum_{j=i}^{h-1} e_j`$. La recursión termina porque $`h - i`$ baja en $`1`$. $`\square`$

**Teorema 2.** Para todo $`0 \le d \le h \le n`$: $`\mathtt{resolver}(d,h) = E[d,h)`$.

*Demostración.* Inducción fuerte sobre $`k = h-d`$.

- **Caso $`k \le u'`$.** Devuelve $`\mathtt{sumaTramo}(d, h, 0.0)`$, que por el Lema 4 es $`0 + E[d,h) = E[d,h)`$.
- **Caso $`k > u'`$.** Por el Lema 1, $`d < m < h`$ y los hijos son menores que $`k`$. Por hipótesis de inducción y la convención sobre `parallel`, `izq` $`= E[d,m)`$ y `der` $`= E[m,h)`$. El resultado es `izq + der` $`= E[d,m) + E[m,h) = E[d,h)`$ por la igualdad de la sección 4.1. $`\square`$

Con $`d = 0`$ y $`h = n`$:

$$
\mathtt{energiaParalela}(s, u) = E[0,n) = E(s) = \mathtt{energiaSecuencial}(s)
$$

**Ejemplo del enunciado.** Con `umbral = 1` y los dos cuerpos: $`\mathtt{resolver}(0,2)`$ parte en $`[0,1)`$ y $`[1,2)`$, que son hojas con valores $`25`$ y $`0`$, y la suma da $`25`$.

### 4.3 `centroDeMasaParalelo`

**Programa.**

```scala
def centroDeMasaParalelo(s: Sistema, umbral: Int): Vector2 = {
  @tailrec
  def sumaTramo(i: Int, hasta: Int, suma: Vector2, masa: Double): (Vector2, Double) =
    if (i >= hasta) (suma, masa)
    else {
      val p = s(i)
      sumaTramo(i + 1, hasta, suma + p.pos * p.masa, masa + p.masa)
    }

  def resolver(desde: Int, hasta: Int): (Vector2, Double) =
    if (hasta - desde > Math.max(umbral, 1)) {
      val mitad = desde + (hasta - desde) / 2
      val (izq, der) = parallel(resolver(desde, mitad), resolver(mitad, hasta))
      (izq._1 + der._1, izq._2 + der._2)
    } else sumaTramo(desde, hasta, Vector2(0, 0), 0.0)

  val (suma, masa) = resolver(0, s.length)
  if (masa == 0.0) Vector2(0, 0) else suma * (1.0 / masa)
}
```

Cada tramo devuelve un **par** (suma ponderada, masa del tramo), y combinar dos tramos es $`\oplus`$, porque `(izq._1 + der._1, izq._2 + der._2)` es $`\mathtt{izq} \oplus \mathtt{der}`$.

**Lema 5 (`sumaTramo`).** $`\mathtt{sumaTramo}(i, h, \Sigma_0, \mu_0) = \big( \Sigma_0 + \sum_{j=i}^{h-1} m_j r_j, \; \mu_0 + \sum_{j=i}^{h-1} m_j \big)`$.

*Demostración.* Igual que el Lema 4, por inducción sobre $`\max(h-i, 0)`$: si $`i \ge h`$ devuelve $`(\Sigma_0, \mu_0)`$; si no, llama con $`(\Sigma_0 + m_i r_i, \; \mu_0 + m_i)`$ y se aplica la hipótesis de inducción. $`\square`$

**Teorema 3.** Para todo $`0 \le d \le h \le n`$: $`\mathtt{resolver}(d,h) = (\Sigma[d,h), \; M[d,h))`$.

*Demostración.* Inducción fuerte sobre $`k = h-d`$.

- **Caso $`k \le u'`$.** Por el Lema 5 con $`(\Sigma_0, \mu_0) = ((0,0), 0)`$, el resultado es $`(\Sigma[d,h), M[d,h))`$.
- **Caso $`k > u'`$.** Por el Lema 1 y la hipótesis de inducción, `izq` $`= (\Sigma[d,m), M[d,m))`$ y `der` $`= (\Sigma[m,h), M[m,h))`$. Al combinar con $`\oplus`$ y usar la igualdad de la sección 4.1 en cada componente:

$$
\mathtt{izq} \oplus \mathtt{der} = (\Sigma[d,m) + \Sigma[m,h), \; M[d,m) + M[m,h)) = (\Sigma[d,h), \; M[d,h)) \qquad \square
$$

Con $`d = 0`$ y $`h = n`$, `(suma, masa)` es $`(\Sigma_s, M)`$, el mismo par que produce `aux` en `centroDeMasaSecuencial` (sección 3.3). Después se aplica exactamente la misma decisión final: origen si $`M = 0`$ y $`\tfrac{1}{M}\Sigma_s`$ en otro caso. Por lo tanto:

$$
\mathtt{centroDeMasaParalelo}(s, u) = C(s) = \mathtt{centroDeMasaSecuencial}(s)
$$

**Ejemplo del enunciado.** Con `umbral = 1`: la hoja $`[0,1)`$ da $`((0,0), 2)`$, la hoja $`[1,2)`$ da $`((8,0), 2)`$, el par combinado es $`((8,0), 4)`$ y el resultado es $`\tfrac{1}{4}(8,0) = (2,0)`$.

### 4.4 Por qué la suma sirve para repartir y el promedio no

**La suma sirve** porque cumple tres propiedades, y el reparto las necesita las tres:

1. **Es aditiva sobre partes disjuntas.** La suma sobre un segmento es la suma de las sumas de sus dos mitades (sección 4.1). Es la razón por la que importa que el corte no pierda ni repita partículas (Lemas 1 y 2): si una partícula faltara o se contara dos veces, esa igualdad dejaría de valer.
2. **Es asociativa** (Lema 3). El árbol de llamadas agrupa los elementos de una forma distinta a la del recorrido de izquierda a derecha del programa secuencial, y asociatividad garantiza que cualquier agrupación da el mismo valor.
3. **Tiene elemento neutro** ($`0`$ o $`((0,0),0)`$). Un segmento vacío o la acumulación inicial de una hoja no cambian el resultado.

**El promedio no sirve directamente.** Sea $`c[A] = \Sigma[A] / M[A]`$ el centro de masa de un conjunto de partículas $`A`$. Combinar los centros de dos mitades como si fueran datos de la misma importancia no da el centro del total, porque cada mitad pesa distinto. El valor correcto es

$$
c[A \cup B] = \frac{M[A]\, c[A] + M[B]\, c[B]}{M[A] + M[B]}
$$

que necesita las masas $`M[A]`$ y $`M[B]`$ además de los promedios, y por eso el código lleva el par $`(\Sigma, M)`$ y divide una sola vez al final.

*Contraejemplo.* Sea $`A`$ una partícula de masa $`1`$ en $`(0,0)`$ y $`B`$ una de masa $`3`$ en $`(4,0)`$. Entonces $`c[A] = (0,0)`$ y $`c[B] = (4,0)`$.

$$
\text{promedio de los promedios} = \frac{(0,0) + (4,0)}{2} = (2,0) \qquad \text{centro de masa real} = \frac{1 \cdot (0,0) + 3 \cdot (4,0)}{1 + 3} = (3,0)
$$

*Además, el promedio de dos valores no es asociativo.* Con $`x \odot y = (x + y)/2`$:

$$
(x \odot y) \odot z = \frac{x}{4} + \frac{y}{4} + \frac{z}{2} \qquad x \odot (y \odot z) = \frac{x}{2} + \frac{y}{4} + \frac{z}{4}
$$

Para $`x = 0`$, $`y = 0`$, $`z = 4`$ los lados valen $`2`$ y $`1`$. Con un operador así, el resultado dependería de la forma del árbol de llamadas, es decir, de `umbral` y del número de elementos, y no sería un valor bien definido para el sistema.

Llevar $`(\Sigma, M)`$ evita ambos problemas: $`\oplus`$ es asociativa y aditiva, y la única división ocurre al final, una vez, sobre el total. Así tampoco se divide por cero en un tramo cuya masa sea $`0`$; solo se decide el caso $`M = 0`$ sobre el sistema completo, igual que en la versión secuencial.

---

## 5. Punto 4: trabajo y profundidad

### 5.1 `trabajo`

**Especificación.** El trabajo es el número total de operaciones, una por partícula: $`W(n) = n`$.

**Programa.** `def trabajo(n: Int): Int = n`. Devuelve $`n`$, que es $`W(n)`$. No depende del umbral, lo que concuerda con el enunciado: repartir no baja el trabajo.

### 5.2 `profundidad`

**Especificación.** Con $`u' = \max(u,1)`$, la profundidad $`P(n,u)`$ es el número de veces que se parte a la mitad hasta llegar al umbral, más el tamaño del tramo que queda sin partir. En cada partición se sigue el tramo mayor, que por el Lema 1 tiene $`\lceil k/2 \rceil`$ elementos:

$$
P(n, u) = \begin{cases} n & \text{si } n \le u' \\ 1 + P(\lceil n/2 \rceil, u) & \text{si } n > u' \end{cases}
$$

**Programa.**

```scala
def profundidad(n: Int, umbral: Int): Int = {
  @tailrec
  def aux(tramo: Int, particiones: Int): Int =
    if (tramo <= Math.max(umbral, 1)) particiones + tramo
    else aux(tramo - tramo / 2, particiones + 1)

  aux(n, 0)
}
```

**Argumento.**

- Estado: $`(t, c)`$ con $`t`$ el tramo actual y $`c`$ las particiones hechas. Estado inicial: $`(n, 0)`$.
- Invariante: $`\mathrm{Inv}(t, c) \equiv P(n,u) = c + P(t,u)`$.
- **Inicio.** $`P(n,u) = 0 + P(n,u)`$.
- **Se conserva.** Si $`t > u'`$, `tramo - tramo / 2` es $`t - \lfloor t/2 \rfloor = \lceil t/2 \rceil`$ y, por la definición de $`P`$, $`P(t,u) = 1 + P(\lceil t/2 \rceil, u)`$. Entonces $`c + P(t,u) = (c+1) + P(\lceil t/2 \rceil, u)`$, que es la invariante para el nuevo estado $`(\lceil t/2 \rceil, c+1)`$.
- **Al final.** Si $`t \le u'`$, $`P(t,u) = t`$, y el programa devuelve $`c + t = c + P(t,u) = P(n,u)`$.
- **Termina.** Mientras $`t > u' \ge 1`$ se tiene $`t \ge 2`$, y para $`t \ge 2`$ se cumple $`\lceil t/2 \rceil \le t - 1`$. El tramo baja estrictamente y nunca es negativo.

**Ejemplos del enunciado.**

$$
\begin{aligned}
P(1024, 1024) &= 1024 \\
P(1024, 512) &= 1 + P(512, 512) = 1 + 512 = 513 \\
P(1024, 1) &= 10 + P(1, 1) = 10 + 1 = 11
\end{aligned}
$$

En el último caso el tramo pasa por $`1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1`$, que son $`10`$ particiones.

### 5.3 `tiempoEstimado`

**Especificación.** La profundidad más el trabajo entre $`p`$ procesadores, con $`p \ge 1`$:

$$
T(n, p, u) = P(n, u) + \frac{W(n)}{p}
$$

**Programa.** `profundidad(n, umbral) + trabajo(n).toDouble / p`. Por la precedencia de los operadores, es $`P(n,u) + (W(n) / p)`$. Como `trabajo(n)` se convierte a `Double` antes de dividir, la división es real y no entera, y no se trunca. Por sustitución y por las secciones 5.1 y 5.2, el valor es $`P(n,u) + W(n)/p = T(n,p,u)`$.

**Ejemplo del enunciado.** $`T(1024, 4, 1) = 11 + 1024/4 = 267`$.

---

## 6. Punto 5: medir la aceleración

Los tiempos que devuelve ScalaMeter dependen de la máquina y de lo que esté corriendo al lado. Por eso aquí se argumenta lo que el programa **calcula a partir de las mediciones**, no el valor de los tiempos.

### 6.1 `medir`

**Especificación.** Con $`r \ge 1`$ repeticiones, sean $`x_1, \ldots, x_r`$ los tiempos medidos (en milisegundos). Devolver su mediana. Si $`x_{(0)} \le x_{(1)} \le \cdots \le x_{(r-1)}`$ es esa misma lista ordenada, la mediana es $`x_{(\lfloor r/2 \rfloor)}`$ (con $`r`$ par se toma el central de mayor valor).

**Programa.**

```scala
def medir[A](repeticiones: Int)(cuerpo: => A): Double = {
  val muestras = Vector.fill(repeticiones) {
    (withWarmer(new Warmer.Default) measure { cuerpo }).value
  }
  muestras.sorted.apply(muestras.length / 2)
}
```

**Argumento.**

1. `Vector.fill(r)(e)` evalúa `e` $`r`$ veces. Como el argumento es por nombre, cada evaluación hace una medición nueva con ScalaMeter, así que `muestras` tiene $`r`$ valores $`x_1, \ldots, x_r`$. `measure` devuelve una cantidad en milisegundos y `.value` extrae su valor numérico.
2. `sorted` devuelve una permutación de `muestras` en orden no decreciente, es decir, $`(x_{(0)}, \ldots, x_{(r-1)})`$.
3. `muestras.length / 2` es $`\lfloor r/2 \rfloor`$, así que `apply(...)` devuelve $`x_{(\lfloor r/2 \rfloor)}`$.
4. Para $`r = 2q + 1`$ impar, el índice es $`q`$: hay $`q`$ valores a su izquierda (menores o iguales) y $`q`$ a su derecha (mayores o iguales), de modo que es la mediana. Para $`r = 3`$, que es el valor que usa `umbralConveniente`, es el elemento del medio.

### 6.2 `aceleracion`

**Especificación.** El cociente entre el tiempo secuencial y el paralelo. Para $`a > 0`$ y $`b > 0`$:

$$
A(a, b) = \frac{a}{b}
$$

**Argumento.** Por sustitución directa, `aceleracion(a, b)` evalúa `a / b`, que es $`A(a,b)`$. Por lo tanto $`\forall a, b > 0 : \mathtt{aceleracion}(a,b) = A(a,b)`$. En particular $`A(a,a) = 1`$; $`A(a,b) < 1`$ cuando $`b > a`$ y $`A(a,b) > 1`$ cuando $`b < a`$.

**Ejemplos del enunciado.** $`A(100, 25) = 4`$ y $`A(100, 100) = 1`$.

### 6.3 `umbralConveniente`

**Programa.**

```scala
def umbralConveniente(s: Sistema, dt: Double, umbrales: List[Int]): Int = {
  @tailrec
  def elegir(mejor: Int, mejorTiempo: Double, candidatos: List[Int]): Int =
    candidatos match {
      case Nil => mejor
      case u :: resto =>
        val tiempo = medir(3)(pasoParalelo(s, dt, u))
        if (tiempo < mejorTiempo) elegir(u, tiempo, resto)
        else elegir(mejor, mejorTiempo, resto)
    }

  umbrales match {
    case Nil => s.length
    case u :: resto => elegir(u, medir(3)(pasoParalelo(s, dt, u)), resto)
  }
}
```

**Especificación.** Sea $`L = [u_1, \ldots, u_k]`$ con $`k \ge 1`$ y sea $`T_i`$ el tiempo que `medir(3)` obtiene para el candidato $`u_i`$. Cada candidato se mide una sola vez en esta llamada: $`u_1`$ en el `match` inicial y los demás en `elegir`. Se pide devolver un candidato de menor tiempo:

$$
u^\star = u_{i^\star} \quad \text{con} \quad i^\star \in \{1, \ldots, k\} \;\wedge\; \forall i \in \{1, \ldots, k\} : T_{i^\star} \le T_i
$$

Se indexa por posición para que una lista con valores repetidos no cause ambigüedad.

**Argumento (lista no vacía).** El programa llama a `elegir` con $`(m, t, C) = (u_1, T_1, [u_2, \ldots, u_k])`$. Después de procesar $`j`$ candidatos, el tercer argumento es $`C_j = [u_{j+1}, \ldots, u_k]`$. La invariante del auxiliar es

$$
\mathrm{Inv}_j(m, t) \equiv \exists\, i_j \le j : \; m = u_{i_j} \;\wedge\; t = T_{i_j} \;\wedge\; \forall i \le j : T_{i_j} \le T_i
$$

Es decir: $`m`$ es uno de los candidatos ya recorridos, $`t`$ es su tiempo y ningún candidato ya recorrido tardó menos.

- **Inicio ($`j = 1`$).** $`m = u_1`$ y $`t = T_1`$, con $`i_1 = 1`$. La condición sobre $`i \le 1`$ es $`T_1 \le T_1`$.
- **Se conserva.** Si $`C_j = u_{j+1} :: C_{j+1}`$, el programa mide $`T_{j+1}`$ y compara con $`t`$:
  - Si $`T_{j+1} < t`$, llama a `elegir(u_{j+1}, T_{j+1}, C_{j+1})`. Se cumple $`\mathrm{Inv}_{j+1}`$ con $`i_{j+1} = j+1`$: $`T_{j+1} < t = T_{i_j} \le T_i`$ para todo $`i \le j`$, y $`T_{j+1} \le T_{j+1}`$.
  - Si $`T_{j+1} \ge t`$, llama a `elegir(m, t, C_{j+1})`. Se cumple $`\mathrm{Inv}_{j+1}`$ con $`i_{j+1} = i_j`$: $`t \le T_i`$ para todo $`i \le j`$ por la hipótesis, y $`t \le T_{j+1}`$ por el caso.
- **Al final.** Cuando el tercer argumento es `Nil`, $`j = k`$. El programa devuelve $`m`$ y, por la invariante, $`m = u_{i_k}`$ con $`T_{i_k} \le T_i`$ para todo $`i \le k`$, que es la especificación. Con empate, la comparación estricta conserva el candidato anterior, que también cumple $`T_{i_k} \le T_i`$.
- **Termina.** La lista $`C`$ pierde un elemento en cada llamada. Además, `elegir` es recursiva de cola: cada llamada recursiva es lo último que se evalúa en su rama.

**Lista vacía.** Por definición del programa:

$$
\mathtt{umbralConveniente}(s, \Delta t, [\,]) = |s| = n
$$

El valor devuelto no pertenece a la lista de candidatos porque no hay candidatos, y eso es lo que pide el enunciado. Además $`n \le \max(n, 1)`$, así que en `pasoParalelo` con $`u = n`$ el primer llamado a `resolver(0, n)` es una hoja y no se reparte nada:

$$
\mathtt{pasoParalelo}(s, \Delta t, n) = \mathtt{pasoSegmento}(s, \Delta t, 0, n) = \mathtt{pasoSecuencial}(s, \Delta t)
$$

Es decir, las dos versiones ejecutan la misma expresión sobre el sistema completo. Por eso, para $`u = n`$, el cociente ideal de tiempos es $`A(a,a) = 1`$ (el valor medido puede variar un poco por el ruido propio de la medición).

---

## 7. Resumen: la versión paralela da el mismo resultado que la secuencial

Para todo sistema $`s`$, todo $`\Delta t`$ y todo umbral $`u`$:

| Versión paralela | Versión secuencial | Demostración |
|---|---|---|
| `pasoParalelo(s, dt, u)` | `pasoSecuencial(s, dt)` | Teorema 1 y sección 2.6: igualdad exacta |
| `energiaParalela(s, u)` | `energiaSecuencial(s)` | Teorema 2: igualdad en $`\mathbb{R}`$ |
| `centroDeMasaParalelo(s, u)` | `centroDeMasaSecuencial(s)` | Teorema 3: igualdad en $`\mathbb{R}^2`$ |

Las tres demostraciones tienen la misma estructura:

1. El Lema 1 asegura que cada corte reparte el segmento en dos partes disjuntas que lo cubren completo, y el Lema 2 extiende eso a todas las hojas: **no se pierde ni se repite ninguna partícula**.
2. La operación que combina los resultados parciales (`++`, la suma de reales o la suma de pares $`\oplus`$) es **asociativa** y tiene elemento neutro (secciones 2.5 y 4.1), así que la forma del árbol de llamadas no altera el resultado.
3. Las hojas calculan el mismo valor que la versión secuencial sobre su segmento (`pasoSegmento`, `sumaTramo`), y el valor del segmento completo es la combinación de los valores de las partes.

Para las dos magnitudes con sumas, la igualdad es en $`\mathbb{R}`$; en `Double` la diferencia entre ambas versiones es de redondeo, y por eso las pruebas comparan con tolerancia. Lo que cambia con el umbral $`u`$ no es el resultado sino el número de hojas y, con él, el tiempo.
