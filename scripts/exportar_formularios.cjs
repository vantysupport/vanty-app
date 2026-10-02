// Exporta los catálogos de formularios de la web (app/admin/data) a JSON para la app Android.
// Se ejecuta con el jiti del proyecto web: node exportar_formularios.cjs <raiz web> <carpeta destino>
const path = require('path')
const fs = require('fs')
const raiz = process.argv[2]
const destino = process.argv[3]
const jiti = require(path.join(raiz, 'node_modules', 'jiti'))(__filename, {
  alias: { '@': raiz },
  interopDefault: true,
  jsx: true,
})
globalThis.React = require(path.join(raiz, 'node_modules', 'react'))
const d = (f) => jiti(path.join(raiz, 'app', 'admin', 'data', f))
const neuro = d('neurodivergentForms.ts'), neuroEn = d('neurodivergentForms-en.ts')
const nuevo = d('newFormConstants.tsx'), nuevoEn = d('newFormConstants-en.tsx')
const forms = d('formConstants.tsx'), formsEn = d('formConstants-en.tsx')

// Solo lo que la app necesita para mostrar el formulario (sin íconos ni JSX)
const limpiar = (secs) => (secs || []).map(s => ({
  title: typeof s.title === 'string' ? s.title : '',
  description: typeof s.description === 'string' ? s.description : undefined,
  questions: (s.questions || []).map(q => ({
    id: q.id, label: typeof q.label === 'string' ? q.label : String(q.label ?? ''), type: q.type,
    placeholder: q.placeholder, options: q.options, min: q.min, max: q.max, required: q.required, helpText: q.helpText,
  })),
}))

function catalogo(en) {
  const out = {}
  for (const f of (en ? neuroEn.ALL_FORMS_EN : neuro.ALL_FORMS)) {
    out[f.id] = { id: f.id, title: f.title, description: f.description, sections: limpiar(f.sections) }
  }
  const def = (id, es, enT, dEs, dEn, secEs, secEn) => { out[id] = { id, title: en ? enT : es, description: en ? dEn : dEs, sections: limpiar(en ? secEn : secEs) } }
  def('objetivo_iep', 'Objetivo IEP', 'IEP Goal', 'Plan de educación individualizado', 'Individualized education plan', nuevo.OBJETIVO_IEP_DATA, nuevoEn.OBJETIVO_IEP_DATA_EN)
  def('nota_sesion', 'Nota de sesión', 'Session note', 'Registro de sesión clínica', 'Clinical session record', nuevo.NOTA_SESION_DATA, nuevoEn.NOTA_SESION_DATA_EN)
  def('informe_mensual', 'Informe mensual de progreso', 'Monthly progress report', 'Evaluación mensual del progreso', 'Monthly progress assessment', nuevo.INFORME_MENSUAL_DATA, nuevoEn.INFORME_MENSUAL_DATA_EN)
  def('registro_conductual', 'Registro conductual ABC', 'ABC behavior record', 'Análisis funcional de conducta', 'Functional behavior analysis', nuevo.REGISTRO_CONDUCTUAL_ABC_DATA, nuevoEn.REGISTRO_CONDUCTUAL_ABC_DATA_EN)
  def('anamnesis', 'Historia clínica', 'Clinical history', 'Anamnesis e historia del desarrollo', 'Anamnesis and developmental history', forms.ANAMNESIS_DATA, formsEn.ANAMNESIS_DATA_EN)
  def('aba', 'Sesión ABA', 'ABA session', 'Registro de sesión de terapia ABA', 'ABA therapy session record', forms.ABA_DATA, formsEn.ABA_DATA_EN)
  def('entorno_hogar', 'Evaluación del entorno del hogar', 'Home environment assessment', 'Evaluación del ambiente familiar', 'Assessment of the family environment', forms.ENTORNO_HOGAR_DATA, formsEn.ENTORNO_HOGAR_DATA_EN)
  def('brief2', 'Evaluación BRIEF-2', 'BRIEF-2 assessment', 'Funciones ejecutivas', 'Executive functions', forms.BRIEF2_DATA, formsEn.BRIEF2_DATA_EN)
  def('ados2', 'Evaluación ADOS-2', 'ADOS-2 assessment', 'Diagnóstico del autismo', 'Autism diagnosis', forms.ADOS2_DATA, formsEn.ADOS2_DATA_EN)
  def('vineland3', 'Evaluación Vineland-3', 'Vineland-3 assessment', 'Conducta adaptativa', 'Adaptive behavior', forms.VINELAND3_DATA, formsEn.VINELAND3_DATA_EN)
  def('wiscv', 'Evaluación WISC-V', 'WISC-V assessment', 'Escala de inteligencia', 'Intelligence scale', forms.WISCV_DATA, formsEn.WISCV_DATA_EN)
  def('basc3', 'Evaluación BASC-3', 'BASC-3 assessment', 'Sistema conductual', 'Behavioral system', forms.BASC3_DATA, formsEn.BASC3_DATA_EN)
  return out
}

fs.mkdirSync(destino, { recursive: true })
for (const [en, nombre] of [[false, 'formularios_es.json'], [true, 'formularios_en.json']]) {
  const c = catalogo(en)
  fs.writeFileSync(path.join(destino, nombre), JSON.stringify(c))
  console.log(nombre, Object.keys(c).length, 'formularios')
}
