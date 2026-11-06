# ERPAGPSapp — App del conductor (Android)

App Kotlin + Jetpack Compose de los conductores de Action Truck. Habla solo con la API `/api/movil/v1` del ERP (repositorio **ERPA1**, privado; contrato en su `docs/api-movil.md`). Cómo compilar, publicar e instalar: `README.md`.

- El repositorio es **público**: nunca se suben la URL del ERP, la clave de firma (`keystore.properties`, `*.jks`) ni ningún otro secreto.
- Los colores salen de `marca/tokens.json`, que es una copia de `packages/ui/tokens.json` del ERP: no se escriben colores a mano.
- Las releases se publican desde el equipo con `scripts/publicar-release.ps1`, porque la clave de firma no sale de allí.

## Seguridad (obligatorio en cada cambio)
Las mismas reglas que el ERP (`docs/seguridad.md` y `CLAUDE.md` de ERPA1), aplicadas a la app:
- **Dependencias**: antes de añadir una, comprobar que existe en Google Maven o Maven Central, que se mantiene, que tiene uso real y que el nombre es exacto (typosquatting o paquetes inventados). Si hay duda, no se añade y se avisa. Versiones exactas en `build.gradle.kts`: nada de `+` ni `latest.release`. Si se resuelve en menos de 30 líneas, no se añade librería. Dependabot avisa de las vulnerables.
- **Secretos**: ninguno en el código, en los commits ni en `Log`. La contraseña no se guarda nunca y el token va en `EncryptedSharedPreferences`. Si aparece un secreto subido: parar y avisar, porque hay que rotarlo.
- **Red**: solo HTTPS (`res/xml/seguridad_red.xml`), sin `usesCleartextTraffic`, y confiando solo en las autoridades del sistema. Solo el APK de pruebas (`src/debug`) admite `http://`.
- **Datos**: los mínimos (posiciones, firmas y fotos de la entrega). Nada de datos personales en los registros. Fuera de la copia en la nube y de la transferencia entre móviles.
- **Componentes**: solo `MainActivity` exportada (la del lanzador). Servicios, proveedores y receptores nuevos, con `exported="false"` salvo que haya un motivo justificado.
- **API**: un cambio en el servidor tiene que seguir valiendo para las versiones de la app ya instaladas. La app tampoco debe depender de campos nuevos sin comprobar que existen.
- **CI** (`.github/workflows/compilar.yml` y `seguridad.yml`, obligatorias antes de fusionar en `main`): compila, gitleaks sobre todo el historial y CodeQL (falla con gravedad ≥ 7).
- **Al terminar cada cambio**, cerrar la respuesta con un bloque **«Seguridad:»**: dependencias añadidas (y cómo se verificaron), permisos de Android nuevos, endpoints usados, secretos tocados y riesgos pendientes. Si algo no se cumple, decirlo explícitamente.
