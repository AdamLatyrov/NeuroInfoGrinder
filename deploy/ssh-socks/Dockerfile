FROM alpine:3.20

RUN apk add --no-cache openssh-client

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=5 \
  CMD nc -z 127.0.0.1 9050 || exit 1

CMD ["sh", "-c", "ssh -i /run/secrets/vpn_proxy_key -o UserKnownHostsFile=/etc/ssh/ssh_known_hosts -o StrictHostKeyChecking=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -N -D 0.0.0.0:9050 root@95.164.93.173"]
