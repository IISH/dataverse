#!/bin/bash
yum update
yes | yum install autoconf automake libtool flex bison gperf git openssl-devel readline-devel

VIRTUOSO_NAME=virtuoso-opensource
VIRTUOSO_VERSION=7.2.0
VIRTUOSO_FULLNAME=$VIRTUOSO_NAME-$VIRTUOSO_VERSION
VIRTUOSO_TARBALL=$VIRTUOSO_FULLNAME_p1.tar.gz
VIRTUOSO_SRCURL=http://sourceforge.net/projects/virtuoso/files/virtuoso/$VIRTUOSO_VERSION/$VIRTUOSO_TARBALL
SRCDIR=/usr/local/src
VIRTUOSO_SRCDIR=$SRCDIR/$VIRTUOSO_FULLNAME
cd $SRCDIR
wget $VIRTUOSO_SRCURL
tar -xzf $VIRTUOSO_TARBALL
CFLAGS="-O2 -m64"
CFLAGS
VIRTUOSOPATH='/opt/virtuoso-7.2.0'
cd $VIRTUOSO_FULLNAME
./configure \
  --program-transform-name="s/isql/isql-vt/" \
  --with-readline \
  --prefix=$VIRTUOSOPATH
make
make install

