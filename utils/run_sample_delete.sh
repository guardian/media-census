#!/bin/bash

docker run --rm -e ASSETSWEEPER_JDBC_URL=jdbc:postgresql://dc1-mmdevdb-02.dc1.gnm.int/asset_folder_importer \
                -e ASSETSWEEPER_JDBC_USER=testuser \
                -e ASSETSWEEPER_PASSWORD=testpassword \
                --network=mediacensus-dev \
                guardianmultimedia/mediacensus-delscanner:DEV