-- Modelos, correo y Telegram pasan de application.yml a la base de datos.
--
-- El motivo es operativo: los cupos gratuitos cambian cada pocas semanas, los proveedores
-- retiran modelos con poco aviso y hace falta reordenar la cadena en caliente. Con la
-- configuración en un fichero, cada ajuste era recompilar y desplegar.

create table proveedor_llm (
    id                       uuid primary key,
    tenant_id                uuid        not null references tenant (id),
    nombre                   text        not null,
    -- Con la versión incluida (…/v1): el cliente sólo añade /chat/completions.
    url_base                 text        not null,
    modelo                   text        not null,
    -- Cifrada con la clave maestra, que vive en el entorno y NO aquí. Sin esa separación el
    -- cifrado no serviría de nada: quien se llevase una copia de la base la traería consigo.
    api_key_cifrada          text,
    activo                   boolean     not null default true,
    orden                    int         not null default 0,
    apto_datos_reales        boolean     not null default false,
    sombra                   boolean     not null default false,
    limite_mensual_llamadas  int         not null default 0,
    creado_en                timestamptz not null default now(),
    actualizado_en           timestamptz not null default now(),
    constraint uq_proveedor_nombre unique (tenant_id, nombre)
);
create index ix_proveedor_orden on proveedor_llm (tenant_id, orden);

-- Una sola fila por tenant. No es clave-valor a propósito: son campos con tipos distintos que
-- se editan juntos en un formulario, y un mapa genérico sólo añadiría conversiones y errores
-- en tiempo de ejecución.
create table ajustes (
    id                     uuid primary key,
    tenant_id              uuid        not null references tenant (id),
    smtp_host              text,
    smtp_puerto            int         not null default 587,
    smtp_usuario           text,
    smtp_password_cifrada  text,
    smtp_starttls          boolean     not null default true,
    correo_remitente       text,
    telegram_token_cifrado text,
    telegram_chat_id       text,
    actualizado_en         timestamptz not null default now(),
    constraint uq_ajustes_tenant unique (tenant_id)
);
