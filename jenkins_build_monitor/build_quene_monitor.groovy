import org.joda.time.DateTimeUtils
import static java.util.concurrent.TimeUnit.MILLISECONDS
import groovy.json.*
import groovy.json.JsonSlurperClassic

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

pipeline{
    agent {
        label 'jenkins-base-slave'
    }
    options {
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
    }
    stages{
        stage('Init'){
            steps{
                script{
                    buildQueueApi = "${JENKINS_URL}queue/api/json?pretty=true"
                    currentTime = DateTimeUtils.currentTimeMillis()
                    now = new Date()
                    currentTimestamp = now.format("yyyy-MM-dd'T'HH:mm:ss", TimeZone.getTimeZone('UTC'))
                }
            }
        }
        stage('GetAllWaitingBuilds') {
            steps {
                script {
                    def url = buildQueueApi
                    withCredentials([usernamePassword(credentialsId: 'public_username', passwordVariable: 'password', usernameVariable: 'username')]) {
                         process = sh(script: "curl -k -u $username:$password $url 2>/dev/null", returnStdout: true)
                    }
                    def processPretty = JsonOutput.prettyPrint(process)
                    buildQueueInfo = jsonParse(processPretty)
                    buildQueueInfo = buildQueueInfo['items']
                }
            }
        }
        stage('AnalysisEachBuild') {
            steps{
                script{
                    print (buildQueueInfo.size())
                    for ( int i = 0; i < buildQueueInfo.size(); i++ ) {
                        try {
                            // if build's stuck is false, then skip
                            if (!buildQueueInfo[i]['stuck']) {
                                continue
                            }
                            //Get some base info
                            def inQueueMilliseconds = buildQueueInfo[i]['inQueueSince']
                            def waitMilliseconds = currentTime - inQueueMilliseconds
                            def waitMinutes = MILLISECONDS.toMinutes(waitMilliseconds)
                            def waitReason = buildQueueInfo[i]['why']
                            //If job is waiting for nest excutor then pass
                            if (waitReason.contains("Waiting for next available executor on ")) {
                                continue
                            }
                            //Already in build
                            if (waitReason.contains("is already in progress")) {
                                continue
                            }
                            //def jobUrl = buildQueueInfo[i]['task']['url']
                            def queueId = buildQueueInfo[i]['id']
                            def ESContent = """{"trigger":1, "queueId":$queueId, "waitMinutes":$waitMinutes, "timestamp":"$currentTimestamp", "waitReason":"$waitReason"}"""

                            // if build's wait over 10min sent slack message
                            if (waitMinutes >= 10) {
                                //Slave Offline
                                skip_white_list = "false"
                                for(int j = 0; j < whitelist.readLines().size(); j++){
                                    white_keyword = whitelist.readLines()[j]
                                    if (waitReason.contains(white_keyword)) {
                                        echo "Skip WhiteList: $white_keyword"
                                        skip_white_list = "yes"
                                        break
                                    }
                                }
                                if (skip_white_list == "yes"){
                                    continue
                                }
                                if (waitReason.matches("All nodes of label(.*) are offline")){
                                    waitReason_details = waitReason.split("‘")[1]
                                    label = waitReason_details.split("’")[0]
                                    print label
                                    slackSend channel: '#jenkins-alerts', color: 'danger', message: "Jobs wait in build queue over 10min, please check it." + ESContent
                                }
                                if (waitReason.contains(" is offline") && waitReason.contains("EC2")) {
                                    waitReason_details = waitReason.split("‘")[1]
                                    node_name = waitReason_details.split("’")[0]
                                    print node_name
                                    withCredentials([usernamePassword(credentialsId: 'api-token', passwordVariable: 'userapitoken', usernameVariable: 'username')]) {
                                        sh """
                                        echo "Delete node: $node_name"
                                        [ -f jenkins-cli.jar ] || wget -q ${JENKINS_URL}/jnlpJars/jenkins-cli.jar
                                        java -jar jenkins-cli.jar -s $JENKINS_URL -auth "$username":"$userapitoken" delete-node "$node_name"
                                        """
                                    }
                                    slackSend channel: '#jenkins-alerts', color: 'danger', message: "Jobs wait in build queue over 10min, and have delete the $node_name, please check it." + ESContent
                                    continue
                                }
                            }
                        } catch (Exception e) {
                            println e
                        }
                    }
                }
            }
        }
    }
    post{
        always{
            cleanWs()
        }
    }
}





