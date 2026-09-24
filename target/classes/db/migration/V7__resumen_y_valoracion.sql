-- El modelo devuelve ahora dos textos en vez de uno: los hechos y el juicio.
--
-- Pedir uno solo no funcionaba: al no distinguirlos, resumía las respuestas y se ahorraba el
-- juicio, que es justo la parte que no se puede sacar de la tabla. `resumen` conserva su
-- significado —los hechos— y el juicio pasa a tener columna propia.
--
-- La misma llamada trae también el veredicto sobre el nombre, que antes costaba una petición
-- aparte. No hay columna nueva para eso: el veredicto se guarda en `candidatura`, donde ya
-- estaba.
alter table evaluacion add column valoracion text;
