-- «Capacidad» no estaba definida: la capacidad de un piso de 3 habitaciones puede ser 3 o 6
-- según lo que decidas ese día, así que el criterio medía la cifra escrita, no el piso.
--
-- El número de habitaciones sí es un dato objetivo, y de ahí sale personas por habitación, que
-- es lo que realmente distingue los casos: 3 adultos en 3 habitaciones no es lo mismo que 6.
alter table anuncio rename column capacidad_personas to habitaciones;
