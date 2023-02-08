#!/bin/bash

git clone https://github.com/aws/s2n-tls.git
cd s2n-tls

# Pick an "env" line from the codebuild/codebuild.config file and run it, in this case choose the openssl-1.1.1 with GCC 9 build
S2N_LIBCRYPTO=openssl-1.1.1 BUILD_S2N=true TESTS=integrationv2 GCC_VERSION=9

sudo codebuild/bin/s2n_install_test_dependencies.sh
codebuild/bin/s2n_codebuild.sh

sudo make install 
