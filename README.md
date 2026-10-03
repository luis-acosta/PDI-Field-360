# PDI Field 360

Aplicación Android propietaria para captura e inspección de campo utilizando una cámara **Insta360 X3**.

## Objetivo

Desarrollar una aplicación móvil que permita al operador controlar una Insta360 X3 desde un dispositivo Android y asociar las capturas 360° con información estructurada de una inspección de campo.

La aplicación está pensada inicialmente para levantamientos e inspecciones industriales, con posibilidad de evolucionar hacia gestión de activos, trazabilidad de evidencias, sincronización en nube, IIoT, visión artificial y gemelos digitales.

## Plataforma inicial

- Android nativo
- Kotlin
- Android Studio
- Insta360 Camera SDK para Android
- Insta360 Media SDK cuando sea necesario para visualización/procesamiento 360°
- Git + GitHub

## MVP — Primera versión

La primera versión buscará implementar la cadena mínima:

1. Ejecutar la aplicación en un teléfono Android físico.
2. Detectar y conectar una Insta360 X3.
3. Consultar el estado de la cámara.
4. Mostrar Live View.
5. Capturar fotografías.
6. Iniciar y detener grabación de video.
7. Mostrar información básica de las capturas.
8. Asociar una captura con datos básicos de campo.

## Datos de campo previstos

Cada inspección podrá incluir progresivamente:

- Proyecto / cliente
- Planta
- Área
- Equipo
- TAG
- Tipo de inspección
- Fecha y hora
- Coordenadas GPS
- Operador
- Observaciones
- Fotografías 360°
- Videos 360°
- Anomalías encontradas

## Arquitectura conceptual

```text
Insta360 X3
     │
     │ Wi-Fi / Bluetooth / USB
     ▼
Insta360 Camera SDK
     │
     ▼
PDI Field 360
Android + Kotlin
     │
     ├── Live View
     ├── Control de cámara
     ├── Foto / Video
     ├── Datos de inspección
     ├── GPS
     └── Almacenamiento local
              │
              ▼
       Sincronización futura
              │
       Backend / Cloud / IIoT
```

## Estrategia de desarrollo

### Fase 1 — Entorno Android

- Instalar Android Studio.
- Configurar Android SDK.
- Crear/compilar la aplicación base.
- Ejecutarla en un teléfono Android mediante ADB.

### Fase 2 — Interfaz inicial

Pantalla principal con:

- Nombre PDI Field 360.
- Estado de la cámara.
- Botón Conectar cámara.
- Área para Live View.
- Botón Capturar.
- Controles básicos de grabación.

### Fase 3 — Insta360 X3

- Incorporar Camera SDK.
- Gestionar permisos Android.
- Detectar/conectar X3.
- Consultar estado.
- Implementar captura.
- Implementar grabación.
- Implementar Live View.

### Fase 4 — Captura de campo

- Proyecto.
- Área.
- Equipo/TAG.
- GPS.
- Fecha/hora.
- Observaciones.
- Registro local de inspecciones.

### Fase 5 — Evolución

- Galería 360°.
- Sincronización con servidor.
- Gestión de usuarios.
- Backend/API.
- Base de datos.
- Integración con mantenimiento/activos.
- MQTT/IIoT.
- IA y visión artificial.
- Gemelo digital / recorrido virtual de planta.

## Principios del proyecto

- **Offline-first:** la captura de campo no debe depender de conexión a Internet.
- **Trazabilidad:** toda evidencia debe quedar asociada a su contexto de inspección.
- **Modularidad:** separar cámara, datos, interfaz y sincronización.
- **Escalabilidad:** comenzar con un MVP pequeño antes de incorporar nube, IA o IIoT.
- **SDK oficial:** la integración con la Insta360 X3 se realizará mediante las interfaces oficiales disponibles de Insta360.

## Estado

**Etapa actual:** inicio del proyecto.

Primer hito:

```text
Android Studio
      ↓
PDI Field 360
      ↓
Compilar
      ↓
Teléfono Android
      ↓
Aplicación funcionando
```

Después:

```text
PDI Field 360
      ↓
Insta360 Camera SDK
      ↓
Insta360 X3
      ↓
Conectar → Live View → Capturar
```

## Autor

**Ing. Luis Acosta**  
PDI Advanced  
Advanced Engineering Solutions
