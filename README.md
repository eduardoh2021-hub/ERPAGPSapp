# ERPAGPSapp · App del conductor (Android)

APK que llevan los conductores de Action Truck. Habla solo con la API móvil del ERP (`/api/movil/v1`); el contrato está en `docs/api-movil.md` del repositorio del ERP (**ERPA1**, privado). Este repositorio es público para que la app pueda buscar sus actualizaciones sin claves: no contiene secretos (la clave de firma y la URL del ERP se quedan en el equipo que compila).

## Qué hace

- **Acceso** con el usuario y la contraseña de conductor del ERP (rol *conductor* enlazado a un conductor activo). El móvil guarda el token cifrado, nunca la contraseña. La URL del servidor se escribe en la pantalla de acceso.
- **Jornada y GPS**: «Iniciar jornada» arranca un servicio con aviso fijo que lee el GPS cada 15 s y envía las posiciones al ERP: cada 30 s en marcha y cada 5 min parado. Sin cobertura las acumula en el móvil (SQLite) y las envía al recuperarla; cada punto lleva su UUID, así que reenviar no duplica. Si el ERP responde 401/403 (sesión caducada o dispositivo dado de baja), la jornada se para y la app pide volver a entrar.
- **Paradas**: todas las paradas del viaje (hechas en verde, la siguiente en rojo) con su hora prevista. La siguiente parada sale destacada, con **Navegar**, que la abre en Google Maps en modo conducción (si no está instalado, en cualquier app que entienda `geo:`), y con un botón que abre en Google Maps la ruta con todas las paradas pendientes (hasta 10).
- **Mapa**: las paradas numeradas y el trazado previsto (pgRouting) sobre MapLibre con teselas OSM; el estilo lo da el ERP (`GET /config`, el mismo que la web). Pulsar una parada abre su orden.
- **Orden**: lugares de carga y entrega (dirección, horario, cita, instrucciones, contacto con botón de llamar), mercancía, historial y documentos.
- **Documentos**: documento de control de la orden y albaranes, en PDF generado por el ERP, que se ven dentro de la app (se amplían con dos dedos) y se pueden compartir o imprimir.
- **Buscar actualizaciones** (pestaña Jornada, y también en la pantalla de acceso): consulta la última release de este repositorio, la compara con la versión instalada, descarga el APK de la arquitectura del móvil y abre el instalador de Android. Al abrir la app se comprueba en silencio y, si hay versión nueva, se avisa. La primera vez Android pide permitir que la app «instale aplicaciones desconocidas». Solo se instala si el APK va firmado con la misma clave que la app instalada.
- **Registrar**: llegada a carga, cargada (firma opcional), llegada a descarga, **entrega con firma en pantalla, nombre y DNI de quien recibe y fotos** (POD), incidencia / ausente / rechazada con su tipo, y notas. Cada registro se guarda primero en el móvil con su UUID y se envía enseguida; sin cobertura sale solo después. La firma acaba en el albarán y en la trazabilidad de la orden.

## Seguridad

Las reglas obligatorias en cada cambio están en `CLAUDE.md` y en `docs/seguridad.md` del ERP. Cómo avisar de un fallo: `SECURITY.md`. Lo aplicado en la app:

- **Solo HTTPS** (`res/xml/seguridad_red.xml`): no hay tráfico en claro y solo se confía en las autoridades de certificación del sistema. La pantalla de acceso rechaza servidores `http://`. El APK de pruebas tiene su propia configuración (`src/debug`), que admite `http://` para un ERP local.
- **Credenciales**: la contraseña no se guarda nunca. El token se guarda cifrado (`EncryptedSharedPreferences` con una clave del almacén de Android). La app y sus datos quedan fuera de la copia en la nube y de la transferencia a otro móvil (`allowBackup="false"`, `reglas_extraccion_datos.xml`).
- **Actualizaciones**: solo se aceptan APK de las releases de este repositorio en `github.com`. Antes de abrir el instalador se comprueba su SHA-256 con el que publica GitHub, y Android exige además la misma firma que la app instalada.
- **Repositorio público**: no hay secretos. La clave de firma y la URL del ERP se quedan en el equipo que compila (`keystore.properties` y `local.properties`, fuera de git).
- **Comprobaciones en cada PR** (`.github/workflows/seguridad.yml`): gitleaks (secretos en todo el historial) y CodeQL (Kotlin y workflows; falla con gravedad ≥ 7), además de `Compilar`. Dependabot (`.github/dependabot.yml`) revisa cada semana las dependencias de Gradle y las acciones. Para que bloqueen la fusión, se marcan como obligatorias en *Settings › Rules* (ver `docs/seguridad.md` §5 del ERP).

### Novedades de la 1.2.0 (seguridad)

- Conexión con el ERP solo por HTTPS: **antes de instalarla, el ERP tiene que estar publicado por HTTPS** (con Caddy, v1.6 del ERP). Si un móvil tenía guardado `http://…`, la app pide la dirección `https://`.
- Comprobación del SHA-256 del APK al actualizar.
- Datos de la app fuera de las copias de seguridad y de la transferencia entre móviles.

## Compilar

Requisitos en este equipo (ya instalados en `C:\Android`): JDK 17 (`C:\Android\jdk17`), Android SDK (`C:\Android\sdk`: plataforma 35, build-tools 35.0.0, platform-tools) y Gradle (el *wrapper* lo descarga solo).

```powershell
cd apps\android
$env:JAVA_HOME = "C:\Android\jdk17"
.\gradlew.bat assembleRelease          # APK firmados para repartir
.\gradlew.bat assembleDebug            # APK de pruebas
```

- `local.properties` (fuera de git) dice dónde está el SDK (`sdk.dir=C:/Android/sdk`) y la URL del ERP que propone la pantalla de acceso (`erpUrl=https://erp.actiontruck.net`). No la ponga en `gradle.properties`: el repositorio es público.
- Salen un APK por arquitectura y uno universal en `app/build/outputs/apk/<tipo>/`. Para repartir a los conductores, **`app-arm64-v8a-release.apk`** vale para casi cualquier móvil actual; `app-universal-release.apk` vale para todos.
- También se puede pasar al compilar: `.\gradlew.bat assembleRelease -PerpUrl=https://erp.actiontruck.net`.
- **Firma**: `keystore.properties` (fuera de git) apunta al almacén de claves `C:\Android\firma\actiontruck-conductor.jks`. **Guarde una copia de los dos ficheros en un sitio seguro**: sin esa clave no se pueden publicar actualizaciones que se instalen encima de la versión que ya tienen los móviles. Sin `keystore.properties`, `assembleRelease` sale sin firmar y solo sirve `assembleDebug`.
- Los colores se generan al compilar desde `marca/tokens.json`, copia de `packages/ui/tokens.json` del ERP: si cambia la marca allí, copie el fichero aquí (tareas `generarMarcaKotlin` y `generarMarcaRes`): ni la interfaz ni el icono llevan colores escritos a mano. La tipografía es Yantramanav (licencia OFL) en `app/src/main/res/font`.

### Compilación en GitHub

Cada PR y cada push a `main` se compilan solos en GitHub Actions (`.github/workflows/compilar.yml`): si la app no compila, la PR sale en rojo. El **APK de pruebas** (debug) queda 14 días en la ejecución (pestaña *Actions* › la ejecución › *Artifacts*). Se instala **junto** a la app de los conductores, sin sustituirla (identificador `net.actiontruck.conductor.pruebas`, versión `…-pruebas`). Las releases firmadas no se compilan en GitHub: la clave de firma no sale de este equipo.

## Publicar una versión nueva

```powershell
cd C:\CLCODE\ERPAGPSapp
.\scripts\publicar-release.ps1 -Version 1.2.0 -Notas "Qué cambia en esta versión"
```

El script exige el árbol de git limpio y una versión mayor que la última publicada; compila los APK firmados con esa versión (`versionCode` = 1.2.0 → 10200), crea y sube la etiqueta `v1.2.0` y publica la release con los cuatro APK (`ActionTruckConductor-1.2.0-arm64-v8a.apk`, `-armeabi-v7a`, `-x86_64` y `-universal`). Los móviles la verán en «Buscar actualizaciones». Necesita `gh` con sesión iniciada y `keystore.properties`.

Las releases se publican desde este equipo y no desde GitHub Actions a propósito: la clave de firma no sale de aquí.

## Dar de alta a un conductor en la app

Los usuarios de la app no se crean en CONFIGURACIÓN › Usuarios (allí solo están los de la web): se gestionan desde la ficha del conductor, en **GESTIÓN › Conductores › (conductor) › App y dispositivos › Acceso a la app**. Hace falta permiso de escritura en Flota (Administración, Tráfico y Gestión).

- **Dar acceso a la app**: se propone un usuario (inicial del nombre y primer apellido, p. ej. `fjimenez`) y se pone una contraseña inicial (8 caracteres como mínimo, con letras y números). Se crea un usuario con perfil *conductor*, enlazado a esa ficha, que entra en la app pero no en la web. Comuníquele usuario y contraseña: el ERP no guarda las contraseñas y no se pueden consultar después.
- **Cambiar contraseña**: si la olvida. Cierra su sesión en el móvil y tendrá que entrar con la nueva.
- **Desbloquear**: tras 5 intentos fallidos la cuenta se bloquea 15 minutos; aquí se desbloquea al momento.
- **Quitar acceso**: no se borra nada (baja lógica); su sesión se cierra y no puede volver a entrar. Sus posiciones, firmas y registros se conservan. «Volver a dar acceso» reactiva el mismo usuario con una contraseña nueva.
- **Dispositivos**: el móvil se da de alta solo la primera vez que el conductor entra. Si lo pierde o lo cambia, revóquelo en la misma pestaña.

Un conductor dado de baja en Flota no puede entrar en la app aunque tenga usuario.

## Instalar en los móviles

1. Copie el APK al móvil (correo, WhatsApp, USB, enlace) y ábralo. Android pedirá permitir «instalar aplicaciones de origen desconocido» para esa app la primera vez.
2. O con el cable USB y la depuración activada: `C:\Android\sdk\platform-tools\adb.exe install -r app-arm64-v8a-release.apk`.
3. Al iniciar la jornada, conceda la **ubicación** («mientras se usa la app» basta: el aviso fijo mantiene el GPS en segundo plano) y las **notificaciones**. En la pestaña Jornada, «Que el ahorro de batería no corte el GPS» lleva al ajuste para excluir la app del ahorro de batería (recomendable en Samsung, Xiaomi y Huawei).

El servidor del ERP tiene que ser accesible desde el móvil **por HTTPS**, en la oficina y en carretera: la misma URL pública (`https://erp.actiontruck.net`), que Caddy publica delante del ERP dejando pasar solo la API de la app (`docs/seguridad.md` §3 del ERP). Desde la versión 1.2.0, la app de los conductores **no admite `http://`**. Solo el APK de pruebas (debug) lo admite, para probar contra un ERP local.

## Probar

- Servidor: `pnpm web`. Conductores de demostración: `mrodriguez` y `aperez`, contraseña `Demo2026` (seed `0012_gps_demo.sql`). Planifique desde la web un viaje de hoy con asignación a uno de ellos para verlo en la app.
- Emulador (APK de pruebas): `C:\Android\sdk\emulator\emulator.exe -avd conductor`; desde el emulador el ERP del equipo es `http://10.0.2.2:3030`. Para probar con un móvil de verdad en la red local (también con el APK de pruebas): arranque el ERP con `ERP_WEB_HOST=0.0.0.0`, que por defecto solo escucha en `127.0.0.1`, y use `http://192.168.x.x:3030`.
- Tests de la API que usa la app (en el repositorio del ERP): `apps/web/test/app_conductor.test.ts` y `apps/web/test/movil.test.ts`.
