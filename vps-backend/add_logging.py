"""Add logging and test Worker connectivity."""
import paramiko
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('107.172.153.83', username='root', password='Password', timeout=15)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=15)
    out = stdout.read()
    err = stderr.read()
    sys.stdout.buffer.write(out[:800])
    if err.strip():
        sys.stdout.buffer.write(b'ERR: ' + err[:300])
    sys.stdout.buffer.write(b'\n')

# Add access logging to Apache config
sftp = ssh.open_sftp()
try:
    with sftp.open('/etc/apache2/conf.d/vanta-proxy.conf', 'r') as f:
        content = f.read().decode()
except:
    content = ""

if 'CustomLog' not in content:
    content += '\n    LogFormat "%h %t \\"%r\\" %>s %b \\"%{User-Agent}i\\"" vanta_combined\n    CustomLog /var/log/vanta-access.log vanta_combined\n'
    with sftp.open('/etc/apache2/conf.d/vanta-proxy.conf', 'w') as f:
        f.write(content)
    print("Added access logging")

sftp.close()

run('apachectl configtest 2>&1')
run('systemctl restart httpd 2>&1')

# Now trigger a request from the Worker and check Apache logs
run('truncate -s 0 /var/log/vanta-access.log 2>/dev/null; echo cleared')

ssh.close()
print('DONE')
