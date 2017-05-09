#!/usr/bin/bash -ex

test -d target/classes -a -d target/lib || mvn package

java -cp 'target/lib/*;target/classes' \
    ledgerdb.scraper.Scraper \
    --kdbx-pw @ledgerdb-scraper.pw \
    --kdbx-file ledgerdb-scraper.kdbx \
    --selenium-driver chrome.ChromeDriver \
    "$@"
