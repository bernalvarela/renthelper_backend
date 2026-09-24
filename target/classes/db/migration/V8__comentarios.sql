-- Apuntes propios sobre una candidatura, normalmente tras conocer a la persona.
--
-- Tabla aparte y no un campo en `candidatura` porque son apuntes CON FECHA: tras la visita
-- escribes una cosa, tres días después otra, y con quince candidatos y dos semanas de por medio
-- saber cuándo dijiste cada una importa.
--
-- Es texto libre sobre una persona identificada, así que cae de lleno en el RGPD: se borra con
-- la candidatura, tanto en la purga por retención como al ejercer el derecho de supresión.
create table comentario_candidatura (
    id             uuid primary key,
    tenant_id      uuid        not null references tenant (id),
    candidatura_id uuid        not null references candidatura (id),
    texto          text        not null,
    creado_en      timestamptz not null default now()
);
create index ix_comentario_candidatura on comentario_candidatura (candidatura_id, creado_en desc);
