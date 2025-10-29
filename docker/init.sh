#!/bin/sh
docker build -t sentinel-dashboard:1.8.8 .
docker-compose up -d