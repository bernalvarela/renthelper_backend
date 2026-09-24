-- Bonificación fiscal por edad del inquilino.
--
-- El tramo y la nota los declara el propietario: el modelo no tiene acceso a internet ni sabe
-- qué día es, así que no puede consultar «las vigentes en el momento» y reproduciría de memoria
-- porcentajes de su entrenamiento sin avisar de que se los inventa.
--
-- Aquí sólo se guarda la regla. Lo único que calcula la aplicación es si las edades declaradas
-- caen dentro del tramo, y el resultado es INFORMATIVO: no suma ni resta puntos. Ordenar
-- candidatos por edad en el acceso a la vivienda es justo lo que prohíbe la Ley 12/2023, por
-- mucho que la propia bonificación sea de base legal.
alter table ajustes add column bonificacion_activa   boolean not null default false;
alter table ajustes add column bonificacion_edad_min int     not null default 18;
alter table ajustes add column bonificacion_edad_max int     not null default 35;
alter table ajustes add column bonificacion_nota     text;
