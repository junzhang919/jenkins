pipeline{
	agent {
		label 'master'
	}
	options {
		timestamps()
		timeout(time: 30, unit: 'MINUTES')
	}
	stages{
		stage('update label details'){
			steps {
				script {
					sh """
					set +x
					echo "Update Label Details in Github"
					cd aws_ec2labels
                    cp ${JENKINS_HOME}/config.xml ./
                    python ec2_label_collect.py config.xml ec2labels.html
                    rm -rf config.xml
                    cp -rf ec2labels.html ${JENKINS_HOME}/userContent/
                    """
				}
			}
		}
		stage('Verify New Label'){
			when {
				expression{
					new_label_name != "master"
				}
			}
			agent {
				label new_label_name
			}
			options {
				skipDefaultCheckout(true)
			}
			steps {
				script {
					sh """
					    set +x
                        whoami
                        hostname -I | awk '{print \$1}'
                        java -version 
                        df -h
                        free -m
                    """
				}
			}
			post {
				always {
					cleanWs()
				}
			}
		}
	}
	post {
		always {
			cleanWs()
		}
	}
}