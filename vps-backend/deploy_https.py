"""Deploy HTTPS reverse proxy on VPS."""
import paramiko
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('107.172.153.83', username='root', password='Password', timeout=15)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=15)
    out = stdout.read()
    err = stderr.read()
    sys.stdout.buffer.write(out[:500])
    if err.strip():
        sys.stdout.buffer.write(b'ERR: ' + err[:300])
    sys.stdout.buffer.write(b'\n')

# Create SSL cert
run('openssl req -x509 -nodes -days 3650 -newkey rsa:2048 '
    '-keyout /etc/apache2/conf.d/vanta.key '
    '-out /etc/apache2/conf.d/vanta.crt '
    '-subj "/CN=vanta-backend" 2>&1')

# Write Apache config
apache_conf = """Listen 8443
<VirtualHost *:8443>
    SSLEngine on
    SSLCertificateFile /etc/apache2/conf.d/vanta.crt
    SSLCertificateKeyFile /etc/apache2/conf.d/vanta.key

    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    Header always set Access-Control-Allow-Origin "*"
    Header always set Access-Control-Allow-Methods "GET, POST, OPTIONS"
    Header always set Access-Control-Allow-Headers "Content-Type, X-Vanta-Secret"
</VirtualHost>
"""

sftp = ssh.open_sftp()
with sftp.open('/etc/apache2/conf.d/vanta-proxy.conf', 'w') as f:
    f.write(apache_conf)
sftp.close()
print('Apache config written')

run('apachectl configtest 2>&1')
run('systemctl restart httpd 2>&1')
run('sleep 1')
run('curl -sk https://localhost:8443/health 2>&1')

ssh.close()
print('DONE')
