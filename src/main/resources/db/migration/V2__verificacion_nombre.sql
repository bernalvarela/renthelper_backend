-- Veredicto sobre si el nombre escrito en el formulario parece real o inventado.
--
-- Es informativo y nunca entra en la puntuación: un nombre infrecuente no dice nada del
-- inquilino, y convertirlo en puntos penalizaría nombres extranjeros.
alter table candidatura add column verificacion_nombre text;
alter table candidatura add column motivo_nombre text;
