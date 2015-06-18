#!/bin/bash

git checkout master
if [[ $? == 0 ]] ; then
	git fetch upstream
	git merge upstream/master
fi

