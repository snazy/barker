#!/bin/sh

cd `dirname $0`

# Create the cluster
ccm create -s -n 3 -v 2.1.5 barker

ccm node1 status

# Setup keyspace and tables
ccm node1 cqlsh <<!
source 'create-tables.cql'
desc keyspace barker
exit
!
