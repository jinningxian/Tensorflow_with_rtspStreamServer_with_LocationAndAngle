/*
package com.ncs.rtspstream;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.xfer.FileSystemFile;

import java.io.File;
import java.io.IOException;

public class ssh_ftp extends AppCompatActivity {

    private String hostname, username, password, port;

    @Override
    protected void onCreate(Bundle savedInstanceState) throws IOException {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ssh_ftp);

        hostname = "192.168.21.194"; //add/change hostname here
        username = "sftpuser"; // sftp user to ssh into here
        password = "q1w2e3r4"; //sftp user password
        port = "22"; // sftp port number

        final SSHClient ssh = new SSHClient();
        try {
            ssh.connect(hostname);
            ssh.authPublickey(System.getProperty("user.name"));
            final String src = System.getProperty("user.home") + File.separator + "test_file";
            final SFTPClient sftp = ssh.newSFTPClient();
            try {
                sftp.put(new FileSystemFile(src), "/tmp");
            } finally {
                sftp.close();
            }
        }
        finally {
            ssh.disconnect();
        }
    }
}
*/
