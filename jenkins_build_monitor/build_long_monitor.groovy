import groovy.json.*
import groovy.json.JsonSlurperClassic
import java.lang.String
import org.joda.time.DateTimeUtils
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

def CheckLongBuilds(def job_url,currentTime){
    def job_api = job_url + "api/json?tree=timestamp"

    for(int j = 0; j < whitelist.readLines().size(); j++){
        white_keyword = whitelist.readLines()[j]
        if (job_url.contains(white_keyword)) {
            echo "Skip check job in whitelist: $job_url"
            return 0
        }
    }
    try{
        def job_process = sh (script: "#!/bin/sh -e\n curl -gsk -u $username:$password $job_api 2>/dev/null", returnStdout: true)
        def job_processPretty = JsonOutput.prettyPrint(job_process)
        def JobBuildInfo = jsonParse(job_processPretty)
        def build_start = JobBuildInfo['timestamp']
        def runMilliseconds = currentTime - build_start
        def runMinutes = MILLISECONDS.toMinutes(runMilliseconds)
        if(runMinutes > check_period.toInteger()){
            echo "$job_url has keep running for ${runMinutes} min."
            return 1
        }else{
            return 0
        }
    }catch (Exception e) {
        echo "Got Exception When Check: $job_url"
        println e
        return 0
    }

}
def GetBuildUrl(def CurrentBuildInfo,currentTime){
    def currentExecutable
    def job_url
    def long_build_list = ""
    def check_list = []
    for ( int i = 0; i < CurrentBuildInfo.size(); i++ ) {
        try{
            def Executors = CurrentBuildInfo[i]['executors']
            def oneOffExecutors = CurrentBuildInfo[i]['oneOffExecutors']
            if (Executors.size() >0){
                for (int j = 0; j < Executors.size(); j++){
                    currentExecutable = Executors[j]['currentExecutable']
                    if (currentExecutable){
                        job_url = currentExecutable['url']
                        if(job_url in check_list){
                            continue
                        }else{
                            check_list.add(job_url)
                        }
                    }
                }
            }
            if (oneOffExecutors.size() >0){
                for (int k = 0; k < oneOffExecutors.size(); k++){
                     currentExecutable = oneOffExecutors[k]['currentExecutable']
                    if (currentExecutable){
                        job_url = currentExecutable['url']
                        if(job_url in check_list){
                            continue
                        }else{
                            check_list.add(job_url)
                        }
                    }
                }
            }
        }catch (Exception e) {
            println e
        }
    }

    if(check_list && check_list.size() >0){
        running_builds_num = check_list.size()
        echo "Current Running Builds Number: $running_builds_num"
        check_list.each {
            check_url = it
            check_res = CheckLongBuilds(check_url,currentTime)
            if (check_res == 1 ){
                long_build_list = check_url + "\n" + long_build_list
            }
        }
    }else{
        print "Current No Running Builds!"
    }

    if(long_build_list){
        echo "Below jobs has keep running for more than $check_period min\n$long_build_list"
        slackSend channel: '#jenkins-alerts', color: '#FF5733', message: "[LongTimeBuilds] Below jobs has keep running for more than " + check_period +" min. Please check with related team.\n" + long_build_list
    }else{
        echo "All current running jobs cost less than $check_period min."
    }

}

pipeline{
    agent{
        label 'master'
    }
    options {
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
    }
    stages{
        stage('GetAllCurrentBuilds'){
            steps{
                script{
                    def BuildApi = "${JENKINS_URL}computer/api/json?tree=computer[executors[currentExecutable[url]],oneOffExecutors[currentExecutable[url]]]"
                    currentTime = DateTimeUtils.currentTimeMillis()
                    try{
                        withCredentials([usernamePassword(credentialsId: 'public_username', passwordVariable: 'password', usernameVariable: 'username')]) {
                            def process = sh(script: "#!/bin/sh -e\n curl -sgk -u $username:$password $BuildApi 2>/dev/null", returnStdout: true)
                            def ProcessPretty = JsonOutput.prettyPrint(process)
                            BuildInfo = jsonParse(ProcessPretty)
                            BuildInfo = BuildInfo['computer']
                        }
                    }catch (Exception e) {
                        println e
                    }
                }
            }
        }
        stage('AnalysisEachBuild') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'public_username', passwordVariable: 'password', usernameVariable: 'username')]) {
                        GetBuildUrl(BuildInfo, currentTime)
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
