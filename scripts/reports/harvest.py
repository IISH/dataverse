#!/usr/bin/env python
import getopt
import json
import urllib2
import sys


class HarvestDataverse:
    api_key = '12345'
    dataverse_endpoint = 'http://localhost'

    def __init__(self, dataverse_endpoint, api_key):
        self.api_key = api_key
        self.dataverse_endpoint = dataverse_endpoint

    # begin
    # Start the procedure with the given root dataverse
    def begin(self, root_dataverse):

        headers = ['DATAVERSE_NAME', 'HANDLE', 'DATASET_ID', 'VERSION', 'PUBLICATION_STATUS', 'DESCRIPTION', 'LABEL', 'NAME', 'CONTENT_TYPE']
        print(','.join([u'"{0}"'.format(header) for header in headers]))

        if root_dataverse is None:
            root_dataverse = ':root'
        self.dataverses(root_dataverse)

    # dataverses
    # Go through each dataverse and select the datasets content: child dataverses and datasets
    def dataverses(self, dataverse_id):

        url = self.dataverse_endpoint + '/api/dataverses/' + dataverse_id + '?key=' + self.api_key
        parent_dataverse = self.getData(url)

        url = self.dataverse_endpoint + '/api/dataverses/' + dataverse_id + '/contents?key=' + self.api_key
        child_dataverses = self.getData(url)
        for dataverse_objects in child_dataverses:
            if dataverse_objects['type'] == 'dataset':
                self.dataset(parent_dataverse, str(dataverse_objects['id']))
            if dataverse_objects['type'] == 'dataverse':
                self.dataverses(str(dataverse_objects['id']))

    # latest dataset
    # Go though all verions in the dataset
    def dataset(self, dataverse, dataset_id):

        url = self.dataverse_endpoint + '/api/datasets/' + dataset_id + '?key=' + self.api_key
        dataset = self.getData(url)
        if 'latestVersion' in dataset:
            url = self.dataverse_endpoint + '/api/datasets/' + dataset_id + '/versions?key=' + self.api_key
            versions = self.getData(url)
            dataverse_objects = {'dataset': dataset, 'versions': versions, 'dataverse': dataverse}
            self.print_dataverse_objects(dataverse_objects)

    # print_dataverse_objects
    # Print out the file details
    def print_dataverse_objects(self, file):

        for versions in file['versions']:
            versionNumber = self.v(versions, 'versionNumber', '0') + '.' + self.v(versions, 'versionMinorNumber', '0')
            _items = [
                file['dataverse']['name'],
                self.v(file['dataset'], 'persistentUrl'),
                self.v(versions, 'id'),
                versionNumber,
                self.v(versions, 'versionState')
            ]
            for version in versions['files']:
                items = list(_items)
                items.extend([
                    self.v(version, 'description'),
                    self.v(version, 'label'),
                    self.v(version['datafile'], 'name'),
                    self.v(version['datafile'], 'contentType')])
                print(','.join([u'"{0}"'.format(item.replace('"', '""')) for item in items]).encode('utf-8'))

    def getData(self, url):
        data = json.load(urllib2.urlopen(url))
        if data and data['status'] == 'OK':
            return data['data']
        else:
            print('Unable to call API.')
            sys.exit(1)


    def v(self, dic, key, default=''):
        if key in dic:
            value = dic[key]
            if self.is_number(value):
                return str(value)
            else:
                return value
        return default


    def is_number(self, s):
        try:
            float(s)
            return True
        except ValueError:
            return False


def usage():
    print('Usage: harvest_dataverse.py -d [dataverse endpoint] -k [api key] -r [root dataverse]')


def main(argv):
    api_key = dataverse_endpoint = root = None

    try:
        opts, args = getopt.getopt(argv, 'd:k:r:h', ['dataverse=', 'key=', 'help', 'root='])

    except getopt.GetoptError:
        usage()
        sys.exit(2)
    for opt, arg in opts:
        if opt in ('-h', '--help'):
            usage()
            sys.exit()
        elif opt in ('-d', '--dataverse'):
            dataverse_endpoint = arg
        elif opt in ('-k', '--key'):
            api_key = arg

    assert api_key
    assert dataverse_endpoint
    print('dataverse_endpoint=' + dataverse_endpoint)

    harvest = HarvestDataverse(dataverse_endpoint, api_key)
    harvest.begin(root)


if __name__ == '__main__':
    main(sys.argv[1:])
