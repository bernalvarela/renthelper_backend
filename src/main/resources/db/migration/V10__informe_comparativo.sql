-- Informe que compara a los finalistas de un anuncio en una sola llamada al modelo.
--
-- `candidaturas` guarda qué letra (A, B, C...) era cada candidatura: al modelo se le mandan
-- barajadas y sin nombre, y es lo que permite devolverle al panel quién es quién. El informe
-- habla de personas concretas, así que se borra con ellas (borrado a mano y purga del RGPD).
create table informe_comparativo (
    id                uuid primary key,
    tenant_id         uuid        not null references tenant (id),
    anuncio_id        uuid        not null references anuncio (id) on delete cascade,
    candidaturas      jsonb       not null,
    contenido         jsonb       not null,
    proveedor         text        not null,
    modelo            text,
    prompt_version    text,
    tokens_entrada    int,
    tokens_salida     int,
    latencia_ms       int,
    creado_en         timestamptz not null default now()
);
create index ix_informe_comparativo_anuncio on informe_comparativo (anuncio_id, creado_en desc);
