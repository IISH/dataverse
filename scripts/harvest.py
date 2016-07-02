#!/usr/bin/env python
import getopt
import json
import urllib2
import sys


class HarvestDataverse:
    api_key = '12345'
    dataverse_endpoint = 'http://localhost'
    HEADERS = {'dataverse': ['name'], 'dataset': ['persistentUrl'],
               'datafile': ['id', 'name', 'contentType', 'originalFormatLabel']}

    def __init__(self, dataverse_endpoint, api_key):
        self.api_key = api_key
        self.dataverse_endpoint = dataverse_endpoint

    # begin
    # Start the procedure with the given root dataverse
    def begin(self, root_dataverse):

        csv_headers = []
        for type in self.HEADERS:
            for header in self.HEADERS[type]:
                key = type + '.' + header
                csv_headers.append('"{0}"'.format(key.upper().replace('"', '""')))
        print(','.join(csv_headers))

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

    # dataset
    # Go though all files in the dataset of the current version
    def dataset(self, dataverse, dataset_id):

        url = self.dataverse_endpoint + '/api/datasets/' + dataset_id + '?key=' + self.api_key
        dataset = self.getData(url)
        if dataset['latestVersion']:
            for files in dataset['latestVersion']['files']:
                file = {'datafile': files['datafile'], 'dataset': dataset, 'dataverse': dataverse}
                self.file(file)

    # file
    # Print out the file details
    def file(self, file):

        csv_headers = []
        for type in self.HEADERS:
            for header in self.HEADERS[type]:
                csv_headers.append('"{0}"'.format(str(file[type][header]).replace('"', '""')))
        print(','.join(csv_headers))

    def getData(self, url):
        data = json.load(urllib2.urlopen(url))
        if data and data['status'] == 'OK':
            return data['data']
        else:
            print('Unable to call API.')
            sys.exit(1)


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
