---
description: Política de comentarios/documentación al escribir o editar código en mini-timer (aplica siempre)
---

APLICA SIEMPRE, no es un slash-command opcional: es un criterio permanente de
calidad al escribir o editar código.

Principio: documentar el PORQUÉ, nunca el "qué" obvio.

1. Código EXISTENTE: no modificar ni eliminar comentarios/documentación previos,
   salvo que el usuario lo pida explícitamente.

2. Código NUEVO (clases, funciones, bloques creados en la sesión): SÍ se permite
   —y se recomienda— agregar comentarios/KDoc que expliquen la intención,
   decisiones de diseño y sutilezas no evidentes.
   - Usar KDoc en clases y funciones públicas.
   - Usar comentarios cortos en lógica no evidente (cálculos, casos borde,
     workarounds de la plataforma).

3. Evitar comentarios redundantes que solo repiten lo que el código ya dice
   (p. ej. `// incrementa i` sobre `i++`).

4. Los comentarios se escriben en español, coherentes con el resto del proyecto.
