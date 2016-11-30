#!/usr/bin/bash -ex

test -d target/classes -a -d target/lib || mvn package

java \
    -Dkdbx.pw=@ledgerdb-scraper.pw \
    -Dkdbx.file=ledgerdb-scraper.kdbx \
    -cp 'target/lib/*;target/classes' \
    ledgerdb.scraper.Scraper "$@"
