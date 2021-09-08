#!/bin/bash

cd evaluator && mvn clean package; cd ..
cd optimiser && mvn clean package; cd ..