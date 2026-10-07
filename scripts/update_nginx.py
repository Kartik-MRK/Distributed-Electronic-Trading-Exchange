import subprocess

NGINX_CONF = """map $http_upgrade $connection_upgrade {
    default upgrade;
    "" close;
}

server {
    listen 80 default_server;
    listen [::]:80 default_server;

    server_name _;

    # API Gateway routes (matches exact /orders or /orders/...)
    location ~ ^/(api|auth|orders|accounts|market-data|internal|admin|actuator|matching)(/|$) {
        proxy_pass http://10.43.2.218:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket route to Gateway
    location /ws/ {
        proxy_pass http://10.43.2.218:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
    }

    # Grafana Dashboards
    location /grafana/ {
        proxy_pass http://10.43.136.30:3000/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    # Next.js Trading Web Frontend
    location / {
        proxy_pass http://10.43.176.123:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
"""

proc = subprocess.run([
    "ssh", "-i", "E:/VPS/openssh_private_key",
    "-o", "StrictHostKeyChecking=no",
    "ubuntu@141.148.223.82",
    "sudo tee /etc/nginx/sites-available/default > /dev/null && sudo nginx -t && sudo systemctl reload nginx"
], input=NGINX_CONF, text=True, capture_output=True)

print("Returncode:", proc.returncode)
print("Stdout:", proc.stdout)
print("Stderr:", proc.stderr)
