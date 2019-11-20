#!/bin/env python
import sys
import os

#from prettytable import PrettyTable
#x = PrettyTable()
#x.field_names = ["CloudName", "LabelName", "Owner", "Project" ,"AMI", "ec2Type", "Executors","instanceCap","idleTime","remoteFS","SecurityGroup","Subnet","iamRole"]

filename = sys.argv[1]
targetfile = sys.argv[2]
try:
  import xml.etree.cElementTree as ET
except ImportError:
  import xml.etree.ElementTree as ET

tree = ET.ElementTree(file = filename)
root = tree.getroot()

strTable = "<html><table>" \
           "<style> table, th, td {border: 1px solid black;border-collapse: collapse;}" \
           + "th, td { padding: 5px; text-align: left;}</style>" \
           + "<tr><th>CloudName</th>" \
           + "<th>LabelName</th><th>Owner</th>" \
           + "<th>Project</th><th>AMI</th>" \
           + "<th>ec2Type</th><th>Executors</th>" \
           + "<th>instanceCap</th><th>idleTime</th>" \
           + "<th>remoteFS</th><th>SecurityGroup</th>" \
           + "<th>Subnet</th><th>iamRole</th></tr>"
#print "| CloudName | LabelName | Owner | Project | AMI | ec2Type | Executors | instanceCap | idleTime | remoteFS | SecurityGroup | Subnet | iamRole |"
#print "| --- | --- | --- | --- | --- | --- |--- | --- | --- | --- | --- | --- | --- |"
for child_of_root in root:
  if child_of_root.tag ==  "clouds":
    for cloud_items in child_of_root:
      if cloud_items.tag == "hudson.plugins.ec2.EC2Cloud":
        for ec2cloud_items in cloud_items:
          item_tag = ec2cloud_items.tag
          item_text = ec2cloud_items.text
          if item_tag == "name":
            ec2_cloud_name = item_text
          elif item_tag == "instanceCap":
            def_instanceCap = item_text
          elif item_tag == "templates":
            for templates in ec2cloud_items:
              temps_tag = templates.tag
              if temps_tag == "hudson.plugins.ec2.SlaveTemplate":
                for label_temp in templates:
                  temp_tag = label_temp.tag
                  temp_text = label_temp.text
                  if temp_tag == "ami":
                    ami = temp_text
                  elif temp_tag == "remoteFS":
                    remoteFS = temp_text
                  elif temp_tag == "type":
                    ec2Type = temp_text
                  elif temp_tag == "labels":
                    labels = temp_text
                  elif temp_tag == "numExecutors":
                    numExecutors = temp_text
                  elif temp_tag == "instanceCap":
                    if temp_text != "2147483647":
                      instanceCap = temp_text
                    else:
                      instanceCap = def_instanceCap
                  elif temp_tag == "idleTerminationMinutes":
                    idleTime = temp_text
                  elif temp_tag == "iamInstanceProfile":
                    iamInstanceProfile = temp_text
                  elif temp_tag == "subnetId":
                    subnet = temp_text
                  elif temp_tag == "securityGroups":
                    sg = temp_text.replace(',',' ')
                  elif temp_tag == "tags":
                    owner = "None"
                    project = "None"
                    for tags_temp in label_temp:
                      check_owner = "No"
                      check_project = "No"
                      for tag_info in tags_temp:
                        tag_name = tag_info.tag
                        tag_text = tag_info.text
                        if check_project == "Yes":
                          project = tag_text
                          check_project = "No"
                        if tag_text == "Project":
                          check_project = "Yes"

                        if check_owner == "Yes":
                          owner = tag_text
                          check_owner = "No"
                        if tag_text == "Owner":
                          check_owner = "Yes"
                if not iamInstanceProfile:
                  iamInstanceProfile = "No"
                strRW = "<tr><td>" + ec2_cloud_name \
                        + "</td><td>" + labels \
                        + "</td><td>" + owner \
                        + "</td><td>" + project \
                        + "</td><td>" + ami \
                        + "</td><td>" + ec2Type \
                        + "</td><td>" + numExecutors \
                        + "</td><td>" + instanceCap \
                        + "</td><td>" + idleTime \
                        + "</td><td>" + remoteFS \
                        + "</td><td>" + sg \
                        + "</td><td>" + subnet \
                        + "</td><td>" + iamInstanceProfile \
                        + "</td></tr>"
                strTable = strTable + strRW
                #x.add_row([ec2_cloud_name,labels,owner,project,ami,ec2Type,numExecutors,instanceCap,idleTime,remoteFS,sg,subnet,iamInstanceProfile])
                #print ("|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|" % (ec2_cloud_name,labels,owner,project,ami,ec2Type,numExecutors,instanceCap,idleTime,remoteFS,sg,subnet,iamInstanceProfile))

strTable = strTable + "</table></html>"

if os.path.exists(targetfile):
  os.remove(targetfile)

hs = open(targetfile, 'w')
hs.write(strTable)