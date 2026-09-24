-- Esperas por proveedor no disponible (cuota agotada, saturación). Se cuentan aparte de los
-- fallos reales: no hay nada roto, sólo hay que volver a intentarlo más tarde.
alter table outbox
    add column aplazamientos int not null default 0;
