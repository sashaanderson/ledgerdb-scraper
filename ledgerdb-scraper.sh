#!/usr/bin/bash -e

java \
    -Dkdbx.pw=@ledgerdb-scraper.pw \
    -Dkdbx.file=ledgerdb-scraper.kdbx \
    -cp 'target/lib/*;target/classes' \
    -enableassertions \
    ledgerdb.scraper.Scraper "$@"
