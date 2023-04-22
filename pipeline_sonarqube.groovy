#!/usr/bin/env groovy

pipeline {
    agent any

    tools { nodejs "nodejs_12" }

    environment {
        BACKUP_FILE_NAME = 'backup'
        BUCKET_NAME = 'bucket-name'
        BUCKET_PREFIX = 'jenkins/'

        SONARQUBE_DIRECTORY = '/etc/sonarqube'
    }

    stages {
        stage('Setting environments variables') {
            steps {
                script {
                    wrap([$class: 'BuildUser']) {
                        try {
                            env.USER_DEPLOYER = BUILD_USER
                        } catch (Throwable e) {
                            echo "Caught ${e.toString()}"
                            env.USER_DEPLOYER = "Jenkins"
                            currentBuild.result = "SUCCESS"
                        }
                    }
                }

                script {
                    withCredentials([string(credentialsId: 'backup_aws_access_key', variable: 'backup_aws_access_key'),
                                     string(credentialsId: 'backup_aws_secret_key', variable: 'backup_aws_secret_key'),
                                     string(credentialsId: 'sonarqube_ssh_host', variable: 'sonarqube_ssh_host'),
                                     string(credentialsId: 'sonarqube_ssh_user', variable: 'sonarqube_ssh_user'),
                                     string(credentialsId: 'sonarqube_db_user', variable: 'sonarqube_db_user'),
                                     string(credentialsId: 'sonarqube_db_pass', variable: 'sonarqube_db_pass'),
                                     string(credentialsId: 'sonarqube_db_name', variable: 'sonarqube_db_name'),]) {
                        env.AWS_ACCESS_KEY_ID = backup_aws_access_key
                        env.AWS_SECRET_ACCESS_KEY = backup_aws_secret_key
                        env.SONARQUBE_SSH_HOST = sonarqube_ssh_host
                        env.SONARQUBE_SSH_USER = sonarqube_ssh_user
                        env.SONARQUBE_DB_USER = sonarqube_db_user
                        env.SONARQUBE_DB_PASS = sonarqube_db_pass
                        env.SONARQUBE_DB_NAME = sonarqube_db_name
                    }
                }
            }
        }

        stage('Download from GitLab') {
            steps {
                slackSend(color: "good", message: "Job: ${JOB_NAME} - starting deployment - User: ${USER_DEPLOYER}")

                git branch: 'master',
                        url: 'https://github.com/MiguelPazo/jenkins_scheduled_backup.git'
            }
        }

        stage('Backup Database') {
            steps {
                sshagent(['ssh_key']) {
                    sh """
                    ssh -tt -o 'StrictHostKeyChecking no' ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST} <<EOF
                        PGPASSWORD="${SONARQUBE_DB_PASS}" pg_dump --host localhost --username ${SONARQUBE_DB_USER} --dbname ${SONARQUBE_DB_NAME} --format=custom --file backup.pgsql
                        tar cfz database.tar.gz backup.pgsql
                        
                        exit
                    EOF                    
                    """

                    sh "scp ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST}:/home/${SONARQUBE_SSH_USER}/database.tar.gz ."

                    sh """
                    ssh -tt -o 'StrictHostKeyChecking no' ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST} <<EOF
                        rm -rf backup.pgsql
                        rm -rf database.tar.gz
                        
                        exit
                    EOF                    
                    """
                }
            }
        }

        stage('Backup SonarQube') {
            steps {
                sshagent(['ssh_key']) {
                    sh """
                    ssh -tt -o 'StrictHostKeyChecking no' ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST} <<EOF
                        mkdir sonar_backup
                        sudo cp -r ${SONARQUBE_DIRECTORY}/* sonar_backup
                        sudo chown ${SONARQUBE_SSH_USER}:${SONARQUBE_SSH_USER} sonar_backup -R
                        tar cfz sonarqube.tar.gz sonar_backup
                        
                        exit
                    EOF                    
                    """

                    sh "scp ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST}:/home/${SONARQUBE_SSH_USER}/sonarqube.tar.gz ."

                    sh """
                    ssh -tt -o 'StrictHostKeyChecking no' ${SONARQUBE_SSH_USER}@${SONARQUBE_SSH_HOST} <<EOF
                        rm -rf sonar_backup
                        rm -rf sonarqube.tar.gz

                        exit
                    EOF
                    """
                }

                sh 'ls -alh'
            }
        }

        stage('Downloading dependecies') {
            steps {
                sh 'npm install'
            }
        }

        stage('Executing node script') {
            steps {
                script {
                    def status = sh([returnStatus: true,
                                     script      : 'npm run sonarqube',
                                     encoding    : 'UTF-8'])

                    if (status != 0) {
                        error "Error executing backup command"
                    }
                }
            }
        }
    }

    post {
        always {
            deleteDir()
        }
        success {
            slackSend(color: "good", message: "Job: ${JOB_NAME} - deploy success - User: ${USER_DEPLOYER}")
        }
        unstable {
            slackSend(color: "warning", message: "Job: ${JOB_NAME} - is unstable - User: ${USER_DEPLOYER}")
        }
        failure {
            slackSend(color: "danger", message: "Job: ${JOB_NAME} - deploy failed - User: ${USER_DEPLOYER}")
        }
        changed {
            slackSend(color: "warning", message: "Job: ${JOB_NAME} - has changed status - User: ${USER_DEPLOYER}")
        }
    }

    triggers {
        cron('10 0 * * *')
    }
}
