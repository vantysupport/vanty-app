# Vanty ABA — app nativa Android

App nativa en Kotlin + Jetpack Compose (sin WebView). Habla directo con Supabase y con las rutas `/api` de vanty.xyz,
igual que la web. Identificador: `xyz.vanty.app` (no se puede cambiar después de publicar).

## Abrir y correr
1. Android Studio → **Open** → esta carpeta. Espera el *Gradle sync*.
2. Elige el emulador o un teléfono con depuración USB y pulsa ▶ **Run**.

`local.properties` tiene la URL y la clave pública de Supabase (la misma `NEXT_PUBLIC_SUPABASE_ANON_KEY` de la web).

## ⚠️ Requisito en la web (subir antes de probar con cuentas reales)
En `Documents\VantyABA-web\VantyABA-main` (versión `main` de GitHub) se modificaron **2 archivos**, que hay que subir a `main`:
- `proxy.ts`: las rutas `/api` aceptan `Authorization: Bearer <token>` cuando no hay cookie (la app no usa cookies).
  Respeta 2FA, centro activo y consentimiento de IA. La web sigue igual.
- `app/api/control/route.ts`: reconoce el token de la app para aplicar las funciones del **plan del centro**.

## Qué incluye
**Todos los roles**, con apartados filtrados por el plan del centro (`/api/control`, igual que la web):
lo que el plan no incluye no aparece. **No hay compras dentro de la app** (todo se compra en la web; requisito de Google Play).
El Cerebro IA es solo para PC. Importar Excel figura como "Disponible en la PC".

- **Familias**: Inicio (racha, métricas, próxima cita) · Citas (pedir cambio, "no podré asistir", videollamada) · ARIA (chat IA,
  con consentimiento) · Chat con el centro · Más: Evaluación inicial (por fases), Practicar (plan semanal y programas),
  Recursos, Formularios (estilo lección), Documentos y Perfil.
- **Especialista**: Hoy (meta del día, siguiente sesión) · Agenda · Pacientes (programas, informes, documentos) · Chat
  (familias y equipo) · Más: ARIA, Inteligencia (predicción, patrones, objetivos), Perfil.
- **Secretaría**: Hoy · Agenda · Cobros (pendientes, registros, nuevo cobro, abonos) · Más: Reportes financieros, Recursos (materiales,
  pedidos de la tienda, catálogo), Perfil.
- **Administración**: Centro (asistencia, alertas, ingresos) · Agenda · Bandeja (alertas y aprobaciones) · Chat · Más: Inteligencia,
  Evaluaciones, Pacientes, Cobros, Reportes financieros, Recursos, Usuarios e invitaciones, Perfil.
- **Entrada**: nombre y Términos (versión vigente), consentimiento de IA, aviso de centro no disponible (sin hablar de pagos) y
  bloqueo por límite de familias.
- **Notificaciones pintadas** (color según el tipo, ARIA, tu nombre): racha, última oportunidad, logros, citas, resumen del día,
  sesión en 15 min, sesiones sin registrar, cobros pendientes.
- **Widgets**: *ARIA* (cambia de ánimo y color según tu racha o tu día) y *Tu resumen* (racha, próxima cita y mensajes, o tu día).
- Español / Inglés como la web, modo oscuro, solo colores de la marca Vanty.
- **Eliminar mi cuenta** (Perfil) con confirmación por contraseña → `/api/suscripcion/eliminar-cuenta`.

Las preguntas de Formularios y Evaluación inicial vienen del código de la web, exportadas a `app/src/main/assets/*.json`.
Si cambian en la web, se vuelven a exportar con `node scripts/exportar_formularios.cjs <carpeta web> app/src/main/assets` (y `exportar_evaluacion.cjs`).

## Pantalla de demostración (solo en la versión de pruebas)
Muestra cada rol con datos de ejemplo, sin internet:
```
adb shell am start -n xyz.vanty.app/xyz.vanty.aba.DemoActivity --es rol especialista --es tab Hoy
```
`rol`: padre · especialista · secretaria · admin. No existe en la versión de Google Play.

## Publicar en Google Play
1. Crear la llave de firma (una sola vez; guárdala bien, sin ella no podrás actualizar la app):
   ```
   keytool -genkey -v -keystore vanty-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias vanty
   ```
2. Crear `keystore.properties` en esta carpeta (no se sube a git):
   ```
   storeFile=vanty-release.jks
   storePassword=...
   keyAlias=vanty
   keyPassword=...
   ```
3. `gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. Play Console: prueba interna → prueba cerrada (cuentas nuevas: 12 testers por 14 días) → producción.
   Política de privacidad `https://vanty.xyz/privacidad`; eliminación de cuenta `https://vanty.xyz/privacidad#eliminar`;
   público adulto (18+), sin anuncios; cuenta de familia de prueba para la revisión.
5. Antes de cada versión nueva, sube `versionCode` y `versionName` en `app/build.gradle.kts`.
