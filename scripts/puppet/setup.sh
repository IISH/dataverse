#!/bin/bash
#
# setup.sh
#
# Ensure that the packager manager's latest repository settings are up to date.
# Install the required puppet modules to provision the dataverse components.
#
# Usage: ./setup [operating system] [environment]
#
# This script will set an empty file '/opt/firstrun'
# Once there, future vagrant provisioning skip the update steps.
# Remove the file '/opt/firstrun' to repeat the update.

OPERATING_SYSTEM=$1
if [ -z "$OPERATING_SYSTEM" ] ; then
    OPERATING_SYSTEM="ubuntu-12"
    echo "Operating system not specified. Assuming ${OPERATING_SYSTEM}"
fi

ENVIRONMENT=$2
if [ -z "$ENVIRONMENT" ] ; then
    ENVIRONMENT="development"
    echo "Environment not specified. Assuming ${ENVIRONMENT}"
fi


# puppet_config
# Set the puppet config to avoid warnings about deprecated templates.
function puppet_config {

    echo "[main]
    environment=${ENVIRONMENT}
    logdir=/var/log/puppet
    vardir=/var/lib/puppet
    ssldir=/var/lib/puppet/ssl
    rundir=/var/run/puppet
    factpath=/lib/facter

    [master]
    # This is a masterless puppet agent'," > /etc/puppet/puppet.conf
}


function main {

    puppet_config

    # We will only update and install in the first provisioning step.
    # If ever you need to update again
    FIRSTRUN=/opt/firstrun
    if [ ! -f $FIRSTRUN ] ; then

        # Before we continue let us ensure we run the latests packages at the first run.
        case $OPERATING_SYSTEM in
            centos-6)
                rpm -ivh https://yum.puppetlabs.com/puppetlabs-release-el-6.noarch.rpm
                yum -y update
                yum -y install puppet
            ;;
            ubuntu-12|precise)
                wget https://apt.puppetlabs.com/puppetlabs-release-precise.deb
                dpkg -i puppetlabs-release-precise.deb
                apt-get -y update
                apt-get -y install puppet
            ;;
            ubuntu-14|trusty)
                wget https://apt.puppetlabs.com/puppetlabs-release-trusty.deb
                dpkg -i puppetlabs-release-trusty.deb
                apt-get -y update
                apt-get -y install puppet
            ;;
            ubuntu-15|vivid)
                wget https://apt.puppetlabs.com/puppetlabs-release-vivid.deb
                dpkg -i puppetlabs-release-vivid.deb
                apt-get -y update
                apt-get -y install puppet
            ;;
            *)
                echo "Operating system ${OPERATING_SYSTEM} not supported."
                exit 1
            ;;
        esac


        # Get the most recent puppet agent.
        sudo puppet resource package puppet ensure=latest

        # Install the puppet modules we need.
        puppet module install lwo-dataverse

        puppet apply /vagrant/scripts/puppet/manifest.pp --debug

        if [[ $? == 0 ]] ; then
            touch $FIRSTRUN
        fi
    else
        echo "Repositories are already updated and puppet modules are installed. To update and reinstall, remove the file ${FIRSTRUN}"
    fi


}

main

exit 0