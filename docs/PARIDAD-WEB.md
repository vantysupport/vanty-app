# Paridad app ↔ web (vanty.xyz)

Regla: cada rol ve en la app **los mismos apartados, con el mismo nombre y orden** que en la web, y puede hacer
**las mismas acciones** (crear, editar, borrar), usando las mismas tablas y rutas `/api`. Diferencias acordadas:
- **Cerebro IA**: solo PC.
- **Importar CSV/Excel**: solo PC (aparece como "Disponible en la PC").
- **Compras de planes y tokens**: solo en la web (Google Play). La tienda de productos físicos del centro sí va.
- Colores: solo los de Vanty.

Estado: ✅ igual a la web · 🟡 parcial · ⬜ falta

**Login**: ✅ correo y contraseña + Google y Microsoft (mismo flujo que /auth/callback). Requiere `vantyaba://login` en Supabase → Auth → Redirect URLs.

**Menús**: ✅ la barra y "Más" de cada rol usan los nombres, el orden y las reglas de la web (jefe/admin, plan del centro).
Se quitaron "Bandeja" y "Evaluaciones", que no existen en el menú web.

## Jefe / Admin (menú web: Inicio · Agenda · Pacientes · Análisis Predictivo · Cerebro IA · Pagos · Reportes Financieros · Recursos Adicionales · Chat Equipo · Sistema: Usuarios · Mi Perfil)
| Apartado web | Componente | Acciones en la web | App |
|---|---|---|---|
| Inicio | DashboardHome | KPIs (pacientes, sesiones hoy, sin sesión 30d, programas ABA), sesiones 7 días, retención, programas activos, alertas clínicas (descartar), próximas citas | ✅ |
| Agenda | CalendarView | crear / editar / reprogramar / cancelar / completar citas, aprobar reprogramaciones | ✅ |
| Pacientes | PatientsView + DocumentosView + ProgramasABAView | crear / editar paciente, documentos (subir, renombrar, borrar), programas ABA y datos de sesión, evaluaciones | ⬜ |
| Análisis Predictivo | InteligenciaHubView | predicciones, patrones, objetivos, reportes, seguridad | 🟡 |
| Cerebro IA | KnowledgeBaseView | — | solo PC |
| Pagos | SecretariaPagos | cobros, paquetes, abonos, tarifas (CRUD), borrar | 🟡 |
| Reportes Financieros | AdminReportesFinancieros | KPIs, gráficos, Excel mensual | 🟡 |
| Recursos Adicionales | ResourcesManagementView / StoreManagementView / CatalogoTerapiasView | CRUD recursos, productos, pedidos, terapias | 🟡 |
| Chat Equipo | ChatEspecialistas | mensajes, adjuntos, borrar | 🟡 |
| Usuarios | UserManagementView + InvitacionesPanel | crear usuario, rol, activar, contraseña, tokens, invitaciones | 🟡 |
| Mi Perfil | ConfiguracionView | perfil, centro, IA, calendarios | ⬜ |
| (botón ARIA) | ARIAFloatingChat | chat IA | 🟡 |

## Especialista / Terapeuta (menú web: Inicio · Agenda · Pacientes · Análisis Predictivo · Chat · Mi Perfil)
| Inicio | EspecialistaHome | resumen del día, envíos | ✅ |
| Agenda | MiAgenda | sus sesiones, conectar Google / Outlook | ✅ |
| Pacientes | MisPacientes | crear / editar paciente, documentos, formularios, evaluaciones (enviar a aprobación) | ⬜ |
| Análisis Predictivo | InteligenciaHubView | ver arriba | 🟡 |
| Chat | ChatConAdmin + ChatFamilias | chat con dirección y familias | 🟡 |
| Mi Perfil | MiPerfil | editar perfil | ⬜ |

## Secretaría (menú web: Inicio · Agenda · Pagos · Rep. Financieros · Recursos Adicionales · Mi Perfil)
| Inicio | SecretariaHome | resumen | ✅ |
| Agenda | CalendarView (igual que admin) | crear / editar citas | ✅ |
| Pagos | SecretariaPagos | ver Admin | 🟡 |
| Rep. Financieros | SecretariaReportes | | 🟡 |
| Recursos Adicionales | Recursos / Tienda / Terapias | | 🟡 |
| Mi Perfil | | | ⬜ |

## Familia (menú web: Inicio · Agenda · Practicar en Casa · Centro de Recursos · Mi Perfil + Evaluación inicial, Programas, Chat, Formularios, Documentos, Tienda)
| Inicio | HomeView | racha, KPIs, próxima cita (estado, reprogramar/cancelar), programas, progreso (dominio, asistencia, horas), resumen de ARIA, mensajes del equipo | ✅ |
| Agenda | MisCitasView + SolicitudCita | resumen del mes, solicitar cambio / cancelar, videollamada, virtual/presencial, contactar al centro | ✅ (sin calendario mensual) |
| Practicar en Casa | EngagementView + ProgramasABAView | plan, práctica | 🟡 |
| Centro de Recursos | ResourcesView + StoreView | recursos, pedir productos | 🟡 |
| Chat | ChatFamilias + ChatInterface (ARIA) | | 🟡 |
| Formularios / Evaluación inicial / Documentos | | | 🟡 |
| Mi Perfil | ProfileView + PerfilModales | editar datos, calendarios, notificaciones | 🟡 |
