# -*- mode: ruby -*-
# vi: set ft=ruby :

# Vagrantfile API/syntax version. Don't touch unless you know what you're doing!
VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|

  config.vm.define "standalone", primary: true do |standalone|
    config.vm.hostname = "standalone"
    standalone.vm.box = "puppet-vagrant-boxes.puppetlabs.com-centos-65-x64-virtualbox-puppet.box"

  config.vm.provider "virtualbox" do |v|
    v.customize ["modifyvm", :id, "--cpus", 2]
    v.customize ["modifyvm", :id, "--memory", "2048"]
  end

    operating_system = "centos"
    if ENV['OPERATING_SYSTEM'].nil?
      puts "OPERATING_SYSTEM environment variable not specified. Using #{operating_system} by default.\nTo specify it in bash: export OPERATING_SYSTEM=debian"
      config.vm.box_url = "http://puppet-vagrant-boxes.puppetlabs.com/centos-65-x64-virtualbox-puppet.box"
      config.vm.box = "puppet-vagrant-boxes.puppetlabs.com-centos-65-x64-virtualbox-puppet.box"
    elsif ENV['OPERATING_SYSTEM'] == 'debian'
      puts "WARNING: Debian specified. Here be dragons! https://github.com/IQSS/dataverse/issues/1059"
      config.vm.box_url = "http://puppet-vagrant-boxes.puppetlabs.com/debian-73-x64-virtualbox-puppet.box"
      config.vm.box = "puppet-vagrant-boxes.puppetlabs.com-debian-73-x64-virtualbox-puppet.box"
    else
      operating_system = ENV['OPERATING_SYSTEM']
      puts "Not sure what do to with operating system: #{operating_system}"
      exit 1
    end

    mailserver = "localhost"
    if ENV['MAIL_SERVER'].nil?
      puts "MAIL_SERVER environment variable not specified. Using #{mailserver} by default.\nTo specify it in bash: export MAIL_SERVER=localhost"
    else
      mailserver = ENV['MAIL_SERVER']
      puts "MAIL_SERVER environment variable found, using #{mailserver}"
    end

    #config.vm.provision "shell", path: "scripts/vagrant/setup.sh"
    #config.vm.provision "shell", path: "scripts/vagrant/setup-solr.sh"
    #config.vm.provision "shell", path: "scripts/vagrant/install-dataverse.sh", args: mailserver
    # FIXME: get tests working and re-enable them!
    #config.vm.provision "shell", path: "scripts/vagrant/test.sh"

    config.vm.network "private_network", ip: "10.0.0.100"
    config.vm.synced_folder ".", "/dataverse"
  end

end
