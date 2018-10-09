#!/bin/bash

if [ ! -d "irtt" ] ; then
	git clone https://github.com/heistp/irtt
fi
cd irtt && GOOS=linux GOARCH=arm GOARM=7 go build cmd/irtt/main.go
cp main ../app/src/main/assets/irtt
