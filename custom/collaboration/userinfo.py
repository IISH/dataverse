#!/usr/bin/python
#
# Copyright (C) 2015 International Institute of Social History.
# @author Vyacheslav Tykhonov <vty@iisg.nl>
#
# This program is free software: you can redistribute it and/or  modify
# it under the terms of the GNU Affero General Public License, version 3,
# as published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
# As a special exception, the copyright holders give permission to link the
# code of portions of this program with the OpenSSL library under certain
# conditions as described in each individual source file and distribute
# linked combinations including the program with the OpenSSL library. You
# must comply with the GNU Affero General Public License in all respects
# for all of the code used other than as permitted herein. If you modify
# file(s) with this exception, you may extend this exception to your
# version of the file(s), but you are not obligated to do so. If you do not
# wish to do so, delete this exception statement from your version. If you
# delete this exception statement from all source files in the program,
# then also delete it in the license file.

from mod_python import apache, util
import psycopg2
import psycopg2.extras
import urllib2 
import simplejson
import json
import sys
import re
import cgi
import os
import smtplib
import ConfigParser

def connect(cparser):
        conn_string = "host='%s' dbname='%s' user='%s' password='%s'" % (cparser.get('config', 'host'), cparser.get('config', 'dbname'), cparser.get('config', 'user'), cparser.get('config', 'password'))

        # get a connection, if a connect cannot be made an exception will be raised here
        conn = psycopg2.connect(conn_string)

        # conn.cursor will return a cursor object, you can use this cursor to perform queries
        cursor = conn.cursor()

        #(row_count, dataset) = load_regions(cursor, year, datatype, region, debug)
        return cursor

def sendmail(receivers, name, message, cparser):
    sender = cparser.get('config', 'sender')
    sender = receivers
    mailhost = cparser.get('config', 'mailhost')
    username = cparser.get('config', 'mailusername')
    password = cparser.get('config', 'mailpassword')

    try:
        smtpObj = smtplib.SMTP(mailhost)
        smtpObj.login(username,password)
        smtpObj.sendmail(sender, receivers, message)
        print "Successfully sent email"
    except SMTPException:
        print "Error: unable to send email"

def find_user(str):
    match = re.search('username', str)
    thisuser = ''
    if match:
        id = str
        thisuser = re.sub('username', '', id)
    return thisuser

def listusers(cursor, username):
        sql = "select * from builtinuser"
	if username:
	    sql = sql + " where username in (" + username + ")"

        # execute
        cursor.execute(sql)
	columns = [i[0] for i in cursor.description]

        # retrieve the records from the database
        data = cursor.fetchall()
        return (columns, data)

def get_users_fordataset(cursor, id):
    data = []
    userx = ''
    if id:
        sql = "select assigneeidentifier from roleassignment where definitionpoint_id='" + str(id) + "'";
        cursor.execute(sql)
        columns = [i[0] for i in cursor.description]

        # retrieve the records from the database
        data = cursor.fetchall()
        for user in data:
            thisuser = user[0]
            thisuser = re.sub('[!@#$]', '', thisuser)
            userx = userx + "'" + thisuser + "',"

    userx = re.sub(',$', '', userx)
    return (userx)

def load_remote_data(apiurl, code, year):
    jsondataurl = apiurl
    
    req = urllib2.Request(jsondataurl)
    opener = urllib2.build_opener()
    f = opener.open(req)
    dataframe = simplejson.load(f)
    return dataframe

def get_users(api):
    users = []
    userx = ''
    userline = load_remote_data(api, '', '')
    for row in userline['data']:
        usersm = row['containedRoleAssignees']
        for user in usersm:
            user = re.sub('[!@#$]', '', user)
            if user not in users:
                users.append(user)
    
    for user in users:
        userx = userx + "'" + user + "',"
    
    userx = re.sub(',$', '', userx)
    return userx

def handler(req):
        req.send_http_header()
        cparser = ConfigParser.RawConfigParser()
 	cpath = '/etc/httpd/dataverse.conf'
        cparser.read(cpath)
        cgicmd = ''
	dataverse = ''
	datasetid = ''
	userIDs = ''
	mailto = ''
	message = ''
        params = util.FieldStorage(req)
	cursor = connect(cparser)
        for i in params.keys():
            cgicmd = cgicmd + '&' + i + '=' + params[i].value
	    if i == 'group':
		dataverse = params[i].value
	    if i == 'datasetID':
		datasetid = params[i].value
	    if i == 'mailto':
		mailto = params[i].value
	    if i == 'message':
		message = params[i].value
	    pID = 0
	    pID = find_user(i)
	    if pID > 0:
		userIDs = userIDs + str(pID) + ', '	

	users = ''
	userlist = ''
	if dataverse:
	    api = cparser.get('config', 'dataverse') + "/api/dataverses/" + dataverse + "/groups?key=" + cparser.get('config', 'key')
	    userlist = get_users(api)
	if datasetid:
	    userlist = get_users_fordataset(cursor, datasetid)

	html = ''
	if userlist:
	    (columns, users) = listusers(cursor, userlist)

	    for user in users:
   	        id = user[0]
   	        firstname = user[4]
   	        lastname = user[5]
   	        username = user[8]	
		thisuser = "username" + str(id)
   	        html = html + "<input type=\"checkbox\" name=\"%s\" value=\"%s\"> %s %s<br>\n" % (thisuser, username, firstname, lastname)
	if mailto:
		html = cgicmd
	if mailto == 'on':
		to_name = "Collaborator"
		receivers = cparser.get('config', 'testreceiver')
		sender = cparser.get('config', 'testsender')
		cgicmd = ''
		dmessage = """From: Dataverse Mail System <%s>
To: %s <%s>
Subject: Collabs message

This is a test e-mail message from Dataverse Mail System.
 %s %s
%s
""" % (sender, to_name, receivers, cgicmd, userIDs, message)

	 	sendmail(receivers, to_name, dmessage, cparser)

        req.write(html)
        return apache.OK

