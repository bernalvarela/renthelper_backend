-- Marca de que la evaluación con IA se saltó por nombre aparentemente inventado.
--
-- No es un descarte: la candidatura queda puntuada por las reglas y el panel ofrece lanzar la
-- evaluación igualmente. La decisión de gastar la llamada es del propietario.
alter table candidatura
    add column llm_omitido_por_nombre boolean not null default false;
