/**
 * Created by Miguel Pazo (https://miguelpazo.com)
 */
const dateFormat = require('dateformat');
const execShPromise = require("exec-sh").promise;
const AWS = require('aws-sdk');
const fs = require('fs');

const backupFilename = process.env.BACKUP_FILE_NAME || 'backup';
const awsAccessKey = process.env.AWS_ACCESS_KEY_ID;
const awsSecretKey = process.env.AWS_SECRET_ACCESS_KEY;
const bucketName = process.env.BUCKET_NAME;
const bucketPrefix = process.env.BUCKET_PREFIX;

const filesBackup = [
    'sonarqube.tar.gz',
    'database.tar.gz'
];


void async function () {
    for (let i in filesBackup) {
        if (!fs.existsSync(filesBackup[i])) {
            console.error(`File ${filesBackup[i]} not exists`);
            return;
        }
    }


    const date = new Date();
    const dateFormated = dateFormat(date, "yyyymmdd_HHMMss");

    const backupFile = `${backupFilename}-${dateFormated}.tar.gz`;
    const compressFiles = filesBackup.join(' ');

    try {
        console.log('Generating backup');
        await execShPromise(`tar cfz ${backupFile} ${compressFiles}`, true);

        console.log('Uploading backup');

        const s3 = new AWS.S3({
            accessKeyId: awsAccessKey,
            secretAccessKey: awsSecretKey
        });

        const fileContent = fs.readFileSync(backupFile);
        const s3Path = (bucketPrefix || '') + dateFormat(date, "yyyy_mm");

        const params = {
            Bucket: bucketName,
            Key: `${s3Path}/${backupFile}`,
            Body: fileContent
        };

        let result = await s3.upload(params).promise();

        await execShPromise(`rm -rf ${backupFile}`, true);

        console.log('File uploaded successfully');
        console.log(result);
    } catch (e) {
        console.log('Error: ', e);
        console.log('Stderr: ', e.stderr);
        console.log('Stdout: ', e.stdout);

        return e;
    }

    return;
}();

