# Backlog de eliminación de código heredado de TV Bro — 2026-10-27

Base: `rewrite/independent-core` @ `6bac762`. Referencia: `truefedex/tv-bro` @ `6cb4b7c`.
Inventario por archivo: [UPSTREAM_TVBRO_INVENTORY_REWRITE_20261027.tsv](UPSTREAM_TVBRO_INVENTORY_REWRITE_20261027.tsv).
Línea base: 162 tests unitarios, todos pasan (`testGenericDebugUnitTest`).

## Estado medido

| Clase | Archivos | Acción |
|---|---|---|
| Kotlin/Java derivados de TV Bro | 79 | Reescribir desde el contrato de comportamiento, no editar el texto original |
| Recursos y textos idénticos a TV Bro | 85 (64 texto + 21 binarios) | Sustituir o regenerar con procedencia propia |
| Archivos de TV Bro sin equivalente | 30 | Ya ausentes del árbol; verificar que no quedan referencias |

Retención de líneas en los 79 derivados: 84 % (9 029 de 10 736). 64 archivos tienen ≥ 80 %.

## Método por archivo

1. Anotar el contrato observable (entradas, salidas, efectos, estados) con los tests existentes; añadir tests que fijen lo que no está cubierto.
2. Escribir la implementación nueva desde ese contrato, sin copiar el texto original.
3. Verificar: tests unitarios, `assembleGenericRelease`, y que el archivo no aparezca en el inventario como derivado.
4. Actualizar el inventario y registrar el archivo como sustituido.

## Criterio de cierre

- Inventario sin entradas `MODIFICADO`, `IDENTICO` ni `IDENTICO_BLOB` para código fuente ni recursos.
- Comprobación del APK: ninguna clase, recurso ni cadena de TV Bro distribuida.
- La licencia de TV Bro (`LICENSE.md`, `NOTICE.md`, crédito en «Acerca de») se retira solo después de esa comprobación y con revisión legal; el historial Git conserva el origen.
- Pruebas en el onn: recorridos N01–N10 y reproducción. No se declara optimización sin medición.

## Limitaciones

- Este backlog no prueba que una reescritura esté libre de expresión heredada. La medición por líneas es una aproximación.
- No se ha validado comportamiento en TV física.
