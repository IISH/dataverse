# Vagrant shell provisioning script
# Tested with Vagrant 1.8.1 for vms with OS Ubuntu 12.04, 14.04 and Centos 6.5
#
# Usage
# -----
#
# $ vagrant up         # to create and run the machine
# $ vagrant provision  # to install new packages or configuration with puppet
#
#
# Environment variables
# ---------------------
#
# OPERATING_SYSTEM
# $ export OPERATING_SYSTEM=centos-6:default|ubuntu-12|ubuntu-14
# 'centos-6' will install Centos 6.5
# 'ubuntu-12' will install Ubuntu 12.04 lts (precise)
# 'ubuntu-14' will install Ubuntu 14.04 lts (trusty)
#
#
# # ENVIRONMENT
# $ export ENVIRONMENT=development:default)|*
# You can set any environment, but only 'my_environment' has hieradata in /conf/puppet. Change the value to try out alternative settings.
#
#
# Trouble shooting
# ----------------
# 1. Error: 'A host only network interface... via DHCP....'
# https://github.com/mitchellh/vagrant/issues/3083
# If you get this error it means you have a conflict between two DHCP providers: your host machine and virtualbox which
# runs the virtual box.
# Suggestion: disable the virtualbox DHCP provider on your host:
# $ VBoxManage dhcpserver remove --netname HostInterfaceNetworking-vboxnet0
#
#
# 2. On Ubuntu the error: 'Failed to mount folders in Linux guest...'
# https://github.com/mitchellh/vagrant/issues/3341
#
# The virtual box cannot mount with /vagrant to your host filesystem.
# Suggestion #1: add a symbolic link in the client and reload the VM.
# On your VM:
# $ sudo ln -s /opt/VBoxGuestAdditions-[the version that is here]/lib/VBoxGuestAdditions /usr/lib/VBoxGuestAdditions
# On our host:
# $ vagrant reload
# Then provision on your host
# $ vagrant provision
#
# If that does not work, try suggestion #2:
# https://www.virtualbox.org/manual/ch04.html
# Install the guest additions on your VM for Ubuntu:
# $ sudo apt-get update
# $ sudo apt-get install virtualbox-guest-dkms
# And on your host
# $ vagrant reload
#
# Or Centos:
# $ wget "http://download.virtualbox.org/virtualbox/4.3.32/VBoxGuestAdditions_4.3.32.iso"
# $ mount VBoxGuestAdditions_4.3.32.iso -o loop /mnt
# $ cd /mnt
# $ ./VBoxLinuxAdditions.run  --nox11
# And on your host
# $ vagrant reload
#
#
# 3. Out of memory when installing R packages.
# Suggestion: increase the 'v.customize ["modifyvm", :id, "--memory", "2048"]' setting.
#
#
# 4. When installing you get a 'Connection failed [IP: .......' error.
# Or downloads seem to timeout.
# Suggestion: check if you are not blocked by a firewall. Or sometimes the internet is just busy. If so, try again:
# $ vagrant provision
#
#
# 5. Using puppet provisioning: on Centos the Error: Execution of '/usr/bin/yum -d 0 -e 0 -y install ...' returned 1: Error: Nothing to do
# yum reports an error when the package is in fact installed. Should this happen then repeat the
# provisioning once:
# $ vagrant provision



VAGRANTFILE_API_VERSION = '2'
HOSTNAME = 'standalone'
DEFAULT_ENVIRONMENT = 'development'
DEFAULT_OPERATING_SYSTEM = 'centos-6'


environment = if ENV['ENVIRONMENT'].nil? or ENV['ENVIRONMENT'] == 'default' then DEFAULT_ENVIRONMENT else ENV['ENVIRONMENT'] end
puts "Target environment #{environment}"

operating_system = if ENV['OPERATING_SYSTEM'].nil? or ENV['OPERATING_SYSTEM'] == 'default' then DEFAULT_OPERATING_SYSTEM else ENV['OPERATING_SYSTEM'] end
if operating_system == 'centos-6'
  box = 'puppet-vagrant-boxes.puppetlabs.com-centos-65-x64-virtualbox-puppet.box'
  box_url = 'http://puppet-vagrant-boxes.puppetlabs.com/centos-65-x64-virtualbox-puppet.box'
#elsif operating_system == 'centos-7'
#  box = 'puppetlabs/centos-7.0-64-puppet'
#  box_url = 'https://atlas.hashicorp.com/puppetlabs'
elsif operating_system == 'ubuntu-12'
  box = "puppetlabs/ubuntu-12.04-64-puppet"
  #box_url = 'https://atlas.hashicorp.com/puppetlabs'
elsif operating_system == 'ubuntu-14'
  box = "puppetlabs/ubuntu-14.04-64-puppet"
  #box_url = 'https://atlas.hashicorp.com/puppetlabs'
else
  puts "Not sure what do to with operating system: #{operating_system}"
  puts 'Use: export OPERATING_SYSTEM=centos-6|ubuntu-12|ubuntu-14'
  exit 1
end
puts "Running on box #{box}"


Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  config.vm.hostname = HOSTNAME
  config.vm.box = box
  config.vm.box_url = box_url
  config.vm.define HOSTNAME, primary: true do | standalone |

    config.vm.provider "virtualbox" do |v|
      v.customize ["modifyvm", :id, "--cpus", 2]
      v.customize ["modifyvm", :id, "--memory", "4096"]
    end

    standalone.vm.box = box

    config.vm.network "private_network", type: "dhcp"
    # Apache 2
    #config.vm.network "forwarded_port", guest: 80, host: 8888
    #config.vm.network "forwarded_port", guest: 443, host: 9999
    # Glassfish admin
    # config.vm.network "forwarded_port", guest: 4848, host: 4848
    # PostgreSQL
    config.vm.network "forwarded_port", guest: 5432, host: 5432
    # Solr
    config.vm.network "forwarded_port", guest: 8983, host: 8983
    # Glassfish
    # config.vm.network "forwarded_port", guest: 8080, host: 8080
    # config.vm.network "forwarded_port", guest: 8181, host: 8188
    # config.vm.network "forwarded_port", guest: 9009, host: 9009

end

  #  config.vm.synced_folder ".", "/etc/puppet/modules/dataverse"


 #   config.vm.provision 'shell', path: 'conf/setup.sh', args: [operating_system, environment, '1']
    # Vagrant/Puppet docs:
    # http://docs.vagrantup.com/v2/provisioning/puppet_apply.html
   # config.vm.provision :puppet do |puppet|
    #  puppet.hiera_config_path = 'conf/hiera.yaml'
     # puppet.manifest_file = 'example.pp'
      #puppet.options = "--verbose --debug --environment #{environment} --reports none"
    #end


end
