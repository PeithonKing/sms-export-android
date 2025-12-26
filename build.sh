#!/usr/bin/env bash

read -s -p "Keystore password: " KEYSTORE_PASSWORD
echo
read -s -p "Key password: " KEY_PASSWORD
echo

export KEYSTORE_PASSWORD
export KEY_PASSWORD

./gradlew assembleRelease

unset KEYSTORE_PASSWORD
unset KEY_PASSWORD
