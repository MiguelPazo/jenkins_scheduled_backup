#!/usr/bin/env groovy

pipeline {
    agent any

    tools { nodejs "nodejs_12" }

    environment {
        BACKUP_FILE_NAME = 'backup'
        BUCKET_NAME = 'bucket-name'
        BUCKET_PREFIX = 'jenkins/'
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
                    withCredentials([
                            string(credentialsId: 'backup_aws_access_key', variable: 'backup_aws_access_key'),
                            string(credentialsId: 'backup_aws_secret_key', variable: 'backup_aws_secret_key'),
                    ]) {
                        env.AWS_ACCESS_KEY_ID = backup_aws_access_key
                        env.AWS_SECRET_ACCESS_KEY = backup_aws_secret_key
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

        stage('Downloading dependecies') {
            steps {
                sh 'npm install'
            }
        }

        stage('Deploying serverless') {
            steps {
                sh 'npm run start'
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
        cron('0 0 * * *')
    }
}
