#!/usr/bin/env python3
import os
import re
import sys
import base64
import subprocess

def main():
    raw = os.environ.get('SSH_KEY', '').strip().strip('\'"`')
    if not raw:
        print("::error::ORACLE_VPS_SSH_KEY secret is EMPTY or NOT FOUND in GitHub Secrets! Please check repository settings.")
        sys.exit(1)

    # 1. Check for legacy PEM encryption header
    if 'ENCRYPTED' in raw or 'DEK-Info' in raw:
        print("::error title=Passphrase Encrypted Key::The SSH key provided in GitHub Secrets is encrypted with a passphrase (PEM Proc-Type: 4,ENCRYPTED). GitHub Actions runners run in non-interactive batch mode and cannot prompt for a passphrase. Please update ORACLE_VPS_SSH_KEY in repository secrets with the passwordless deploy key saved at E:\\VPS\\dete_deploy_key.")
        sys.exit(1)

    # 2. Extract base64 payload and check OpenSSH cipher
    m = re.search(r'(-----BEGIN [A-Z0-9 ]+PRIVATE KEY-----)(.*?)(-----END [A-Z0-9 ]+PRIVATE KEY-----)', raw, re.DOTALL)
    if m:
        h = m.group(1).strip()
        b = re.sub(r'\s+', '', m.group(2))
        f = m.group(3).strip()
    else:
        b = re.sub(r'\s+', '', raw)
        h = '-----BEGIN OPENSSH PRIVATE KEY-----'
        f = '-----END OPENSSH PRIVATE KEY-----'

    try:
        decoded = base64.b64decode(b)
        if decoded.startswith(b'openssh-key-v1\0'):
            offset = 15
            cipher_len = int.from_bytes(decoded[offset:offset+4], 'big')
            offset += 4
            cipher = decoded[offset:offset+cipher_len].decode('ascii', errors='ignore')
            if cipher != 'none':
                print(f"::error title=Passphrase Encrypted Key::The SSH key provided in GitHub Secrets is encrypted with a passphrase (cipher: {cipher}). GitHub Actions runners run in non-interactive batch mode and cannot prompt for a passphrase. Please update ORACLE_VPS_SSH_KEY in repository secrets with the passwordless deploy key saved at E:\\VPS\\dete_deploy_key.")
                sys.exit(1)
    except Exception as e:
        # If base64 decoding fails here, ssh-keygen below will validate
        pass

    # 3. Format and write out ~/.ssh/id_rsa
    ssh_dir = os.path.expanduser('~/.ssh')
    os.makedirs(ssh_dir, exist_ok=True)
    key_path = os.path.join(ssh_dir, 'id_rsa')
    pub_path = os.path.join(ssh_dir, 'id_rsa.pub')

    lines = [b[i:i+64] for i in range(0, len(b), 64)]
    fixed = h + '\n' + '\n'.join(lines) + '\n' + f + '\n'

    with open(key_path, 'w', newline='\n') as out:
        out.write(fixed)
    try:
        os.chmod(key_path, 0o600)
    except Exception:
        pass
    print(f"Successfully formatted SSH key ({len(lines) + 2} lines).")

    # 4. Validate key and derive public key using ssh-keygen
    res = subprocess.run(
        ['ssh-keygen', '-y', '-f', key_path],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    if res.returncode != 0:
        err = res.stderr.strip()
        if 'passphrase' in err.lower():
            print(f"::error title=Passphrase Encrypted Key::The SSH key provided in GitHub Secrets is encrypted with a passphrase. Headless GitHub Actions runners cannot enter passphrases in batch mode. Please update ORACLE_VPS_SSH_KEY in repository secrets with the passwordless deploy key saved at E:\\VPS\\dete_deploy_key. Details: {err}")
        else:
            print(f"::error title=Key Validation Error::Failed to derive public key: {err}")
        sys.exit(1)

    with open(pub_path, 'w') as out:
        out.write(res.stdout)
    try:
        os.chmod(pub_path, 0o644)
    except Exception:
        pass

    fp_res = subprocess.run(
        ['ssh-keygen', '-l', '-f', pub_path],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    if fp_res.returncode == 0:
        fp = fp_res.stdout.strip()
        print(f"::notice title=Deploy Key Fingerprint::{fp}")
        print(f"Key fingerprint: {fp}")

if __name__ == '__main__':
    main()
