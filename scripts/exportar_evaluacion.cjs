// Exporta las secciones de la Evaluación inicial (app/padre/components) a JSON para la app Android.
// node exportar_evaluacion.cjs <raiz web> <carpeta destino>
const path = require('path')
const fs = require('fs')
const raiz = process.argv[2]
const destino = process.argv[3]
const ts = require(path.join(raiz, 'node_modules', 'typescript'))

/** Extrae el literal `const NOMBRE...= [ ... ]` de un archivo y lo evalúa (sin tipos). */
function extraer(archivo, nombre) {
  const src = fs.readFileSync(path.join(raiz, 'app', 'padre', 'components', archivo), 'utf8')
  const ini = src.search(new RegExp(`(export\\s+)?const\\s+${nombre}\\s*:[^=]*=\\s*\\[`))
  if (ini < 0) throw new Error(`${nombre} no encontrado en ${archivo}`)
  let i = src.indexOf('[', src.indexOf('=', ini)), nivel = 0, fin = -1, enCadena = null
  for (let k = i; k < src.length; k++) {
    const c = src[k]
    if (enCadena) { if (c === '\\') { k++; continue } if (c === enCadena) enCadena = null; continue }
    if (c === '"' || c === "'" || c === '`') { enCadena = c; continue }
    if (c === '[') nivel++
    else if (c === ']') { nivel--; if (nivel === 0) { fin = k; break } }
  }
  const literal = src.slice(i, fin + 1)
  const js = ts.transpileModule(`module.exports = ${literal}`, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  const m = { exports: null }
  new Function('module', 'exports', js)(m, m.exports)
  return m.exports
}

const salida = {
  es: {
    intake: extraer('EvaluacionInicialView.tsx', 'SECCIONES_INTAKE_ES'),
    psico: extraer('EvaluacionInicialView.tsx', 'SECCIONES_PSICO_ES'),
    neuro: extraer('EvaluacionInicialView.tsx', 'SECCIONES_NEURO_ES'),
  },
  en: {
    intake: extraer('evaluacion-inicial-en.ts', 'SECCIONES_INTAKE_EN'),
    psico: extraer('evaluacion-inicial-en.ts', 'SECCIONES_PSICO_EN'),
    neuro: extraer('evaluacion-inicial-en.ts', 'SECCIONES_NEURO_EN'),
  },
}
for (const idioma of ['es', 'en']) {
  fs.writeFileSync(path.join(destino, `evaluacion_${idioma}.json`), JSON.stringify(salida[idioma]))
  const s = salida[idioma]
  console.log(idioma, Object.entries(s).map(([k, v]) => `${k}: ${v.length} secciones, ${v.reduce((a, x) => a + x.preguntas.length, 0)} preguntas`).join(' | '))
}
