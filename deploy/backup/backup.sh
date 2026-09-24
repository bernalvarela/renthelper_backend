#!/bin/sh
#
# Copia de seguridad de la base de datos de RentHelper.
#
# Lo lanza cron cada noche (ver el servicio "backup" del docker-compose.yml) y se puede lanzar a
# mano cuando haga falta:
#
#     docker compose exec backup sh /opt/backup/backup.sh
#
# La clave maestra (RENTHELPER_CLAVE_MAESTRA) NO va aquí a propósito: sin ella, los secretos
# cifrados del volcado no sirven de nada a quien se lo lleve. Guárdala aparte.
#
set -eu

DIR="${BACKUP_DIR:-/backups}"
KEEP="${BACKUP_KEEP_DAYS:-14}"
STAMP="$(date +%F_%H%M)"
DB_FILE="$DIR/renthelper-db-$STAMP.dump"

log() { echo "[$(date '+%F %T')] $*"; }

log "Copia de seguridad en $DIR (se conservan $KEEP días)"
mkdir -p "$DIR"

# Se escribe con un nombre temporal y sólo al terminar bien se le pone el definitivo: así una
# copia a medias —el disco se llenó, se paró el contenedor— nunca se confunde con una buena.
log "Volcando la base de datos $POSTGRES_DB..."
PGPASSWORD="$POSTGRES_PASSWORD" pg_dump \
    --host="${POSTGRES_HOST:-boxvault-db}" \
    --username="$POSTGRES_USER" \
    --format=custom \
    --file="$DB_FILE.parcial" \
    "$POSTGRES_DB"
mv "$DB_FILE.parcial" "$DB_FILE"
log "  $(du -h "$DB_FILE" | cut -f1) en $(basename "$DB_FILE")"

# Sólo se borra lo viejo cuando la copia de hoy ha salido bien: si algo falla, se acumulan copias
# en vez de quedarse sin ninguna. Los ".parcial" de un intento fallido sí se limpian.
log "Borrando copias de más de $KEEP días..."
find "$DIR" -name 'renthelper-db-*.dump' -mtime "+$KEEP" -print -delete
find "$DIR" -name '*.parcial' -mtime +1 -print -delete

log "Hecho. Copias guardadas: $(find "$DIR" -name 'renthelper-db-*.dump' | wc -l)"
