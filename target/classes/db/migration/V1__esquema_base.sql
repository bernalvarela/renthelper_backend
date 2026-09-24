-- Todas las tablas llevan tenant_id desde el principio aunque hoy sólo haya un valor.
-- Añadirlo después a ocho tablas con datos y revisar todas las consultas cuesta mucho más
-- que llevarlo desde la primera migración.

create table tenant (
    id          uuid primary key,
    nombre      text        not null,
    creado_en   timestamptz not null default now()
);

create table usuario (
    id            uuid primary key,
    tenant_id     uuid        not null references tenant (id),
    email         text        not null unique,
    password_hash text        not null,
    rol           text        not null default 'ADMIN',
    creado_en     timestamptz not null default now()
);

-- Esquema de formulario. Versionado: una candidatura fija la versión que rellenó, así
-- las respuestas antiguas siguen siendo interpretables cuando cambias el formulario.
create table form_schema (
    id           uuid primary key,
    tenant_id    uuid        not null references tenant (id),
    nombre       text        not null,
    version      int         not null,
    definicion   jsonb       not null,
    publicado_en timestamptz,
    creado_en    timestamptz not null default now()
);
create index ix_form_schema_tenant on form_schema (tenant_id);

-- Rúbrica de puntuación. Mismo criterio de versionado: permite repuntuar en lote y
-- comparar rankings cuando cambias las reglas.
create table rubrica (
    id           uuid primary key,
    tenant_id    uuid        not null references tenant (id),
    nombre       text        not null,
    version      int         not null,
    definicion   jsonb       not null,
    publicada_en timestamptz,
    creado_en    timestamptz not null default now()
);
create index ix_rubrica_tenant on rubrica (tenant_id);

create table anuncio (
    id                     uuid primary key,
    tenant_id              uuid          not null references tenant (id),
    slug                   text          not null unique,
    titulo                 text          not null,
    direccion              text,
    renta_mensual          numeric(10, 2) not null,
    capacidad_personas     int,
    disponible_desde       date,
    idiomas                text[]        not null default '{es}',
    idioma_por_defecto     text          not null default 'es',
    form_schema_id         uuid          not null references form_schema (id),
    rubrica_id             uuid          not null references rubrica (id),
    -- El interruptor que evita tener que despublicar el anuncio en idealista cuando te
    -- saturas: el enlace deja de aceptar altas pero los que empezaron pueden terminar.
    aceptando_candidaturas boolean       not null default true,
    umbral_alerta          int           not null default 80,
    ultimo_digest_en       timestamptz,
    creado_en              timestamptz   not null default now()
);
create index ix_anuncio_tenant on anuncio (tenant_id);

create table candidatura (
    id                       uuid primary key,
    tenant_id                uuid        not null references tenant (id),
    anuncio_id               uuid        not null references anuncio (id),
    form_schema_id           uuid        not null references form_schema (id),
    -- El token va en la URL, no sólo en cookie: el enlace se abre dentro del navegador
    -- embebido de idealista, donde las cookies pueden no sobrevivir al cierre de la app.
    token                    text        not null unique,
    nombre                   text        not null,
    telefono_normalizado     text,
    email                    text,
    idioma                   text        not null default 'es',
    estado                   text        not null default 'BORRADOR',
    paso_actual              int         not null default 0,
    respuestas               jsonb       not null default '{}'::jsonb,
    -- Descarte automático desactivado: no cambia el estado, sólo marca y ordena al fondo.
    no_cumple_minimos        boolean     not null default false,
    motivos_minimos          jsonb,
    puntuacion               int,
    -- Candidatura de prueba. Los proveedores con apto_datos_reales=false sólo evalúan estas.
    sintetica                boolean     not null default false,
    puntuacion_manual        int,
    creada_en                timestamptz not null default now(),
    actualizada_en           timestamptz not null default now(),
    enviada_en               timestamptz,
    notificada_en            timestamptz,
    revisada_en              timestamptz,
    segundos_cumplimentacion int,
    ip_alta                  text
);
-- Deduplicación: con 150 contactos habrá gente que abra el enlace tres veces.
create unique index ux_candidatura_telefono
    on candidatura (anuncio_id, telefono_normalizado)
    where telefono_normalizado is not null;
create index ix_candidatura_anuncio_estado on candidatura (anuncio_id, estado);
create index ix_candidatura_tenant on candidatura (tenant_id);

-- 1:N sobre candidatura: una fila PRINCIPAL (la que puntúa) y N en SOMBRA para comparar
-- modelos sin afectar al ranking.
create table evaluacion (
    id                      uuid primary key,
    tenant_id               uuid        not null references tenant (id),
    candidatura_id          uuid        not null references candidatura (id) on delete cascade,
    rol                     text        not null,
    proveedor               text        not null,
    modelo                  text,
    puntuacion_determinista int         not null,
    ajuste_llm              int         not null default 0,
    puntuacion_total        int         not null,
    desglose                jsonb,
    resumen                 text,
    banderas                jsonb,
    preguntas_pendientes    jsonb,
    confianza               text,
    rubrica_version         int,
    prompt_version          text,
    tokens_entrada          int,
    tokens_salida           int,
    latencia_ms             int,
    raw_response            jsonb,
    error                   text,
    creada_en               timestamptz not null default now()
);
create index ix_evaluacion_candidatura on evaluacion (candidatura_id, rol);
create index ix_evaluacion_tenant on evaluacion (tenant_id);

-- Outbox transaccional: el envío del formulario no puede depender de que el LLM y
-- Telegram respondan. Se escribe en la misma transacción que la candidatura.
create table outbox (
    id                uuid primary key,
    tenant_id         uuid        not null references tenant (id),
    tipo              text        not null,
    referencia_id     uuid,
    payload           jsonb,
    estado            text        not null default 'PENDIENTE',
    intentos          int         not null default 0,
    ultimo_error      text,
    proxima_ejecucion timestamptz not null default now(),
    creado_en         timestamptz not null default now(),
    procesado_en      timestamptz
);
create index ix_outbox_pendiente on outbox (estado, proxima_ejecucion);

-- Analítica de abandono. Con 150 personas rellenando desde el móvil, saber en qué paso
-- se caen es lo que te dice si el formulario es demasiado largo.
create table evento_formulario (
    id             bigserial primary key,
    tenant_id      uuid        not null references tenant (id),
    candidatura_id uuid        not null references candidatura (id) on delete cascade,
    tipo           text        not null,
    paso           int,
    ocurrido_en    timestamptz not null default now()
);
create index ix_evento_candidatura on evento_formulario (candidatura_id);

-- Presupuesto por proveedor y mes, para la cadena de fallback.
create table consumo_llm (
    id        bigserial primary key,
    tenant_id uuid    not null references tenant (id),
    proveedor text    not null,
    periodo   text    not null,
    llamadas  int     not null default 0,
    fallos    int     not null default 0,
    unique (tenant_id, proveedor, periodo)
);
