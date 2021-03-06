
#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2020 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2020 The MITRE Corporation                                      #
#                                                                           #
# Licensed under the Apache License, Version 2.0 (the "License");           #
# you may not use this file except in compliance with the License.          #
# You may obtain a copy of the License at                                   #
#                                                                           #
#    http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                           #
# Unless required by applicable law or agreed to in writing, software       #
# distributed under the License is distributed on an "AS IS" BASIS,         #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  #
# See the License for the specific language governing permissions and       #
# limitations under the License.                                            #
#############################################################################

echo "Running the MPF workflow manager package post-integration-test script for ActiveMQ KahaDB"

# possible ActiveMQ data locations

paths=("/opt/activemq/data" "/opt/apache-activemq-5.9.0/data")

if ! [ -z "${ACTIVEMQ_HOME}" ]; then
    paths+=("${ACTIVEMQ_HOME}/data")
fi

# try to wipe ActiveMQ queues by deleting KahaDB

for i in "${paths[@]}"
do
    echo "Looking for ActiveMQ KahaDB in $i"

    if [ -d $i ] ; then
        echo "Deleting $i/kahadb"
        sudo rm -rf "$i/kahadb"
        exit 0
    fi
done

echo "Cannot find ActiveMQ KahaDB"

exit 1
