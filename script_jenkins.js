/**
 * Created by Miguel Pazo (https://miguelpazo.com)
 */
const dateFormat = require('dateformat');
const execShPromise = require("exec-sh").promise;
const AWS = require('aws-sdk');
const fs = require('fs');

const backupFilename = process.env.BACKUP_FILE_NAME || 'backup';
const basePath = process.env.JENKINS_HOME;
const awsAccessKey = process.env.AWS_ACCESS_KEY_ID;
const awsSecretKey = process.env.AWS_SECRET_ACCESS_KEY;
const bucketName = process.env.BUCKET_NAME;
const bucketPrefix = process.env.BUCKET_PREFIX;

const directories = [
    '*.xml',
    'jobs/*/*.xml',
    'nodes',
    'plugins/*.jpi',
    'secrets/*',
    'users/*'
];


void async function () {
    if (!basePath) {
        console.error('JENKINS_HOME not declared');
        return;
    }

    const date = new Date();
    const dateFormated = dateFormat(date, "yyyymmdd_HHMMss");

    const backupDirName = `${backupFilename}-${dateFormated}`;
    const backupDir = `${basePath}/${backupDirName}`;
    const backupFile = `${backupFilename}-${dateFormated}.tar.gz`;

    const directoriesCompress = directories.join(' ');

    let catchError = false;

    try {
        console.log('Generating backup');

        await execShPromise(`mkdir ${backupDir}`, true);
        await execShPromise(`cd ${basePath} && tar cfz ${backupFile} ${directoriesCompress}`, true);
        await execShPromise(`mv ${basePath}/${backupFile} ${backupDir}`, true);
        await execShPromise(`cd ${backupDir} && tar xvf ${backupFile}`, true);
        await execShPromise(`cd ${backupDir} && rm -rf ${backupFile}`, true);
        await execShPromise(`cd ${basePath} && tar cfz ${backupFile} ${backupDirName}`, true);
        await execShPromise(`rm -rf ${backupDir}`, true);

        console.log('Uploading backup');

        const s3 = new AWS.S3({
            accessKeyId: awsAccessKey,
            secretAccessKey: awsSecretKey
        });

        const fileContent = fs.readFileSync(`${basePath}/${backupFile}`);
        const s3Path = (bucketPrefix || '') + dateFormat(date, "yyyy_mm");

        const params = {
            Bucket: bucketName,
            Key: `${s3Path}/${backupFile}`,
            Body: fileContent
        };

        let result = await s3.upload(params).promise();

        console.log('File uploaded successfully');
        console.log(result);
    } catch (e) {
        catchError = true;
        console.error('Error: ', e);
    }

    await execShPromise(`rm -rf ${basePath}/${backupFile}`, true);
    console.log(`Deleted local file ${basePath}/${backupFile}`);

    if (catchError) {
        process.exit(1);
    }
}();

