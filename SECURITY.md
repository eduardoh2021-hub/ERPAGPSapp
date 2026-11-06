# Política de seguridad

## Avisar de una vulnerabilidad

**No abra una issue pública.** Use *Security › Report a vulnerability* de este repositorio (aviso privado) o escriba al responsable de Action Truck. Indique qué ha encontrado, en qué versión de la app y cómo reproducirlo.

Si ha visto una clave subida al repositorio (por ejemplo, la de firma de los APK), avise enseguida: hay que **rotarla**.

## Versiones con soporte

Solo la última release publicada. La app la ofrece sola en «Buscar actualizaciones».

## Medidas

Ver la sección **Seguridad** del README: solo HTTPS, token cifrado y fuera de las copias, actualizaciones verificadas por SHA-256 y firma, y gitleaks, CodeQL y Dependabot en cada PR. La API del servidor y su publicación en internet se describen en `docs/seguridad.md` del ERP (repositorio ERPA1, privado).
